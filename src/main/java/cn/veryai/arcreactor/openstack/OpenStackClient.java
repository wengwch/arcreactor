package cn.veryai.arcreactor.openstack;

import cn.veryai.arcreactor.actor.instance.model.NetworkPreparation;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.ResponseException;
import org.openstack4j.api.types.Facing;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.actions.RebuildOptions;
import org.openstack4j.model.compute.ext.Hypervisor;
import org.openstack4j.model.identity.v3.Project;
import org.openstack4j.model.identity.v3.Token;
import org.openstack4j.model.manila.Access;
import org.openstack4j.model.manila.Share;
import org.openstack4j.model.manila.ShareCreate;
import org.openstack4j.model.manila.ShareExportLocation;
import org.openstack4j.model.manila.actions.AccessOptions;
import org.openstack4j.model.network.*;
import org.openstack4j.model.network.SecurityGroup;
import org.openstack4j.model.network.builder.NetworkBuilder;
import org.openstack4j.model.network.builder.SubnetBuilder;
import org.openstack4j.model.network.options.PortListOptions;
import org.openstack4j.model.network.ext.NetQosPolicyBandwidthLimitRule;
import org.openstack4j.model.network.ext.PortForwarding;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.openstack.internal.OSClientSession;
import org.openstack4j.openstack.networking.domain.NeutronTrunkSubport;
import org.openstack4j.openstack.networking.domain.ext.NeutronNetQosPolicy;
import org.openstack4j.openstack.networking.domain.ext.NeutronNetQosPolicyBandwidthLimitRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenStackClient {

  private static final long TOKEN_REFRESH_MARGIN_MILLIS = Duration.ofMinutes(1).toMillis();
  private static final String NOVA_OPERATION_ID = "arcreactor_operation_id";
  private static final Executor NOVA_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-nova").start(task);
  private static final Executor SHARE_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-share").start(task);
  private static final Executor PROJECT_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-project").start(task);
  private static final Executor NETWORK_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-network").start(task);

  public record ManagedNetworkResource(String id, boolean owned) implements Serializable {
    public ManagedNetworkResource {
      if (id == null || id.isBlank()) throw new IllegalStateException("Missing network resource ID");
    }
  }
  public record ForwardingTarget(String floatingIpAddress, String securityGroupId,
      Set<Integer> occupiedPorts) implements Serializable {
    public ForwardingTarget { occupiedPorts = Set.copyOf(occupiedPorts); }
  }

  public record ServerTarget(String clusterId, String projectId, String region)
      implements Serializable {}

  public record CreateServerRequest(
      ServerTarget target,
      String instanceId,
      String requestId,
      String operationId,
      String name,
      String imageId,
      String flavorId,
      String hypervisorId,
      Map<String, String> metadata,
      String networkPortId,
      String userData) implements Serializable {
    public CreateServerRequest {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    public CreateServerRequest(ServerTarget target, String instanceId, String requestId,
        String operationId, String name, String imageId, String flavorId, String hypervisorId,
        Map<String, String> metadata, String networkPortId) {
      this(target, instanceId, requestId, operationId, name, imageId, flavorId, hypervisorId,
          metadata, networkPortId, null);
    }
    public CreateServerRequest(ServerTarget target, String instanceId, String requestId,
        String operationId, String name, String imageId, String flavorId, String hypervisorId,
        Map<String, String> metadata) {
      this(target, instanceId, requestId, operationId, name, imageId, flavorId, hypervisorId, metadata, null);
    }

    @Override
    public String toString() {
      return "CreateServerRequest[instanceId=" + instanceId + ", operationId=" + operationId
          + ", userData=<redacted>]";
    }
  }

  public record NovaServer(String serverId, Status status, String fault) implements Serializable {
    public enum Status {
      BUILD, ACTIVE, ERROR, OTHER
    }
  }

  public record ServerLookupResult(Status status, NovaServer server) implements Serializable {
    public enum Status {
      FOUND, NOT_FOUND
    }
  }

  private final Cache<AdminClientKey, Token> adminTokenCache =
      Caffeine.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(Duration.ofMinutes(30))
          .build();

  private record AdminClientKey(String region, String projectId) {}

  @Autowired private OpenstackClusterRepo clusterRepo;

  @Autowired private RegionRepo regionRepo;

  @Autowired private HypervisorHostRepo hypervisorHostRepo;

  public OSClient.OSClientV3 getOsAdminClient(String region, String projectId) {
    Token token =
        adminTokenCache
            .asMap()
            .compute(
                new AdminClientKey(region, projectId),
                (key, cachedToken) -> {
                  if (cachedToken != null
                      && cachedToken.getExpires() != null
                      && cachedToken.getExpires().getTime()
                          > System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MILLIS) {
                    return cachedToken;
                  }
                  return authenticateAdmin(key.region(), key.projectId());
                });
    // Sessions have mutable headers/perspective and thread-local state; only share the token.
    return OSFactory.clientFromToken(token).useRegion(region);
  }

  private Token authenticateAdmin(String region, String projectId) {
    OpenstackClusterEntity openstackClusterEntity = getOpenstackCluster(region);
    return OSFactory.builderV3()
        .endpoint(openstackClusterEntity.getAuthEndpoint())
        .credentials(
            openstackClusterEntity.getAdminUsername(),
            openstackClusterEntity.getAdminPassword(),
            Identifier.byId(openstackClusterEntity.getAdminDefaultDomainId()))
        .scopeToProject(Identifier.byId(projectId))
        .authenticate()
        .getToken();
  }

  public OpenstackClusterEntity getOpenstackCluster(String region) {
    RegionEntity regionEntity = regionRepo.findById(region);
    return clusterRepo.findById(regionEntity.getClusterId());
  }

  public List<? extends Hypervisor> listHypervisors(String region) {
    OpenstackClusterEntity cluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getAdminDefaultProjectId());
    if (osClient instanceof OSClientSession.OSClientSessionV3 clientSessionV3) {
      HashMap<String, String> map = new HashMap<>();
      map.put("OpenStack-API-Version", "compute 2.87");
      clientSessionV3.headers(map);
    }
    return osClient.compute().hypervisors().list();
  }

  public CompletionStage<Void> grantProjectAdminRole(String region, String projectId) {
    return CompletableFuture.runAsync(() -> {
      OpenstackClusterEntity cluster = getOpenstackCluster(region);
      requireServerText(cluster.getAdminRoleId(), "adminRoleId");
      // Authenticate in the existing admin project before granting access to the new project.
      OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getAdminDefaultProjectId());
      ActionResponse response = osClient.identity().roles().grantProjectUserRole(
          projectId, osClient.getToken().getUser().getId(), cluster.getAdminRoleId());
      if (response == null || !response.isSuccess()) {
        throw new IllegalStateException("Project administrator role assignment was not confirmed");
      }
    }, PROJECT_EXECUTOR);
  }

  public CompletionStage<Project> createProject(String region, String name) {
    return createProject(region, name, null);
  }

  public CompletionStage<Project> createProject(String region, String name, String operationId) {
    return CompletableFuture.supplyAsync(() -> {
      OpenstackClusterEntity cluster = getOpenstackCluster(region);
      OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getAdminDefaultProjectId());
      return osClient.identity().projects().create(Builders.project()
          .domainId(cluster.getAdminDefaultDomainId()).name(name).enabled(true)
          .description(operationId == null ? null : projectOperationMarker(operationId)).build());
    }, PROJECT_EXECUTOR);
  }

  public CompletionStage<Project> findProjectByOperationId(String region, String name, String operationId) {
    return CompletableFuture.supplyAsync(() -> {
      requireServerText(operationId, "operationId");
      OpenstackClusterEntity cluster = getOpenstackCluster(region);
      OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getAdminDefaultProjectId());
      Project project = osClient.identity().projects().getByName(name, cluster.getAdminDefaultDomainId());
      if (project == null) return null;
      if (!name.equals(project.getName()) || !cluster.getAdminDefaultDomainId().equals(project.getDomainId())
          || !projectOperationMarker(operationId).equals(project.getDescription())) {
        throw new OpenStackOperationException("Project name belongs to another creation operation", null);
      }
      return project;
    }, PROJECT_EXECUTOR);
  }

  private static String projectOperationMarker(String operationId) {
    return "arcreactor:user-project:" + operationId;
  }

  public void updateProject(String region, String projectId, String name) {
    OSClient.OSClientV3 osClient = getOsAdminClient(region, projectId);
    osClient.identity().projects().update(Builders.project().id(projectId).name(name).build());
  }

  public SecurityGroup getSecurityGroup(String region, String projectId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client.networking().securitygroup().list(Map.of("project_id", projectId)).stream()
        .filter(s -> s.getName().equalsIgnoreCase("default"))
        .findFirst()
        .orElse(null);
  }

  public void deleteSecurityGroupRule(String region, String projectId, String securityGroupRuleId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    client.networking().securityrule().delete(securityGroupRuleId);
  }

  public SecurityGroupRule addSecurityGroupRule(
      String region, String projectId, String securityGroupId, int port, NetProtocol protocol) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    SecurityGroupRule sgr =
        client
            .networking()
            .securityrule()
            .list(Map.of("security_group_id", securityGroupId))
            .stream()
            .filter(
                securityGroupRule ->
                    protocol.name().equalsIgnoreCase(securityGroupRule.getProtocol()))
            .filter(
                securityGroupRule ->
                    securityGroupRule.getPortRangeMin() != null
                        && securityGroupRule.getPortRangeMax() != null)
            .filter(
                securityGroupRule ->
                    port >= securityGroupRule.getPortRangeMin()
                        && port <= securityGroupRule.getPortRangeMax())
            .findFirst()
            .orElse(null);
    if (sgr != null) {
      return sgr;
    }
    return client
        .networking()
        .securityrule()
        .create(
            Builders.securityGroupRule()
                .securityGroupId(securityGroupId)
                .protocol(protocol.name().toLowerCase())
                .direction("ingress")
                .ethertype("IPv4")
                .portRangeMin(port)
                .portRangeMax(port)
                .remoteIpPrefix("0.0.0.0/0")
                .build());
  }

  public CompletionStage<Void> deletePortForwarding(String region, String floatingIpId, String portForwardingId) {
    return CompletableFuture.runAsync(() -> {
      try {
        ActionResponse response = getOsForwardingClient(region).networking().floatingip()
            .portForwarding().delete(floatingIpId, portForwardingId);
        requireDeleteResponse(response);
      } catch (ResponseException error) {
        if (error.getStatus() != 404) throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<PortForwarding> createPortForwarding(
      String region,
      String floatingIpId,
      int externalPort,
      String fixedIp,
      int internalPort,
      String internalPortId,
      NetProtocol protocol) {

    return CompletableFuture.supplyAsync(() -> getOsForwardingClient(region).networking()
        .floatingip().portForwarding().create(floatingIpId, Builders.portForwarding()
            .externalPort(externalPort).internalIpAddress(fixedIp).internalPort(internalPort)
            .internalPortId(internalPortId).protocol(protocol.name().toLowerCase(Locale.ROOT)).build()),
        NETWORK_EXECUTOR);
  }

  /** Validates the tenant port and preserves all of its existing security group bindings. */
  public CompletionStage<ForwardingTarget> prepareForwardingTarget(String region, String projectId,
      String floatingIpId, String portId, String fixedIp, String securityGroupId) {
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 admin = getOsForwardingClient(region);
      NetFloatingIP fip = admin.networking().floatingip().get(floatingIpId);
      if (fip == null || fip.getFloatingIpAddress() == null || fip.getFloatingIpAddress().isBlank()
          || (fip.getPortId() != null && !fip.getPortId().isBlank())) {
        throw new OpenStackOperationException("Floating IP is missing or directly bound to a port", null);
      }
      OSClient.OSClientV3 tenant = getOsAdminClient(region, projectId);
      Port port = tenant.networking().port().get(portId);
      if (port == null || !projectId.equals(port.getTenantId()) || fixedIp.contains(":")) {
        throw new OpenStackOperationException("Forwarding requires an IPv4 port in the target project", null);
      }
      if (port.getFixedIps() == null
          || port.getFixedIps().stream().noneMatch(ip -> fixedIp.equals(ip.getIpAddress()))) {
        throw new OpenStackOperationException("Forwarding IP is not assigned to the target port", null);
      }
      SecurityGroup group = securityGroupId == null
          ? getSecurityGroup(region, projectId) : tenant.networking().securitygroup().get(securityGroupId);
      if (group == null || !projectId.equals(group.getTenantId())) {
        throw new OpenStackOperationException("Security group is missing from the target project", null);
      }
      if (Boolean.FALSE.equals(port.isPortSecurityEnabled())) {
        throw new OpenStackOperationException("Target port must have port security enabled", null);
      }
      List<String> groups = new ArrayList<>(Objects.requireNonNullElse(port.getSecurityGroups(), List.of()));
      if (!groups.contains(group.getId())) {
        groups.add(group.getId());
        var builder = Builders.port();
        groups.forEach(builder::securityGroup);
        Port update = builder.build();
        update.setId(portId);
        if (tenant.networking().port().update(update) == null) {
          throw new IllegalStateException("Neutron returned no updated port");
        }
      }
      Set<Integer> occupied = new HashSet<>();
      // SDK sessions also carry thread-local context; restore the shared-IP project after tenant calls.
      for (PortForwarding rule : getOsForwardingClient(region).networking().floatingip()
          .portForwarding().list(floatingIpId)) {
        occupied.add(rule.getExternalPort());
      }
      return new ForwardingTarget(fip.getFloatingIpAddress(), group.getId(), occupied);
    }, NETWORK_EXECUTOR);
  }

  /** After an uncertain create, allowCreate=false performs lookup only. */
  public CompletionStage<ManagedNetworkResource> ensurePortForwarding(String region, String floatingIpId,
      int externalPort, String fixedIp, int internalPort, String portId, NetProtocol protocol,
      String marker, boolean allowCreate) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsForwardingClient(region).networking().floatingip().portForwarding();
      List<? extends PortForwarding> matches = service.list(floatingIpId).stream()
          .filter(rule -> marker.equals(rule.getDescription())).toList();
      if (matches.size() > 1) throw new IllegalStateException("Duplicate forwarding operation marker");
      if (!matches.isEmpty()) {
        PortForwarding found = matches.getFirst();
        if (found.getExternalPort() != externalPort || found.getInternalPort() != internalPort
            || !fixedIp.equals(found.getInternalIpAddress()) || !portId.equals(found.getInternalPortId())
            || !protocol.name().equalsIgnoreCase(found.getProtocol())) {
          throw new OpenStackOperationException("Forwarding operation marker has mismatched parameters", null);
        }
        return new ManagedNetworkResource(found.getId(), true);
      }
      if (!allowCreate) return null;
      PortForwarding created = service.create(floatingIpId, Builders.portForwarding()
          .externalPort(externalPort).internalIpAddress(fixedIp).internalPort(internalPort)
          .internalPortId(portId).protocol(protocol.name().toLowerCase(Locale.ROOT))
          .description(marker).build());
      if (created == null || created.getId() == null) throw new IllegalStateException("Missing forwarding ID");
      return new ManagedNetworkResource(created.getId(), true);
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<ManagedNetworkResource> ensureForwardingSecurityRule(String region,
      String projectId, String groupId, int internalPort, NetProtocol protocol,
      String marker, boolean allowCreate) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsAdminClient(region, projectId).networking().securityrule();
      List<? extends SecurityGroupRule> rules = service.list(Map.of("security_group_id", groupId));
      List<? extends SecurityGroupRule> owned = rules.stream()
          .filter(rule -> marker.equals(rule.getDescription())).toList();
      if (owned.size() > 1) throw new IllegalStateException("Duplicate security rule marker");
      if (!owned.isEmpty()) {
        SecurityGroupRule rule = owned.getFirst();
        if (!coversForwarding(rule, groupId, internalPort, protocol)) {
          throw new OpenStackOperationException("Security rule marker has mismatched parameters", null);
        }
        return new ManagedNetworkResource(rule.getId(), true);
      }
      for (SecurityGroupRule rule : rules) {
        if (coversForwarding(rule, groupId, internalPort, protocol)) {
          return new ManagedNetworkResource(rule.getId(), false);
        }
      }
      if (!allowCreate) return null;
      SecurityGroupRule created = service.create(Builders.securityGroupRule().securityGroupId(groupId)
          .protocol(protocol.name().toLowerCase(Locale.ROOT)).direction("ingress").ethertype("IPv4")
          .portRangeMin(internalPort).portRangeMax(internalPort).remoteIpPrefix("0.0.0.0/0")
          .description(marker).build());
      if (created == null || created.getId() == null) throw new IllegalStateException("Missing security rule ID");
      return new ManagedNetworkResource(created.getId(), true);
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<Boolean> deleteManagedPortForwarding(String region, String floatingIpId,
      String ruleId, String marker) {
    return CompletableFuture.supplyAsync(() -> {
      if (ruleId == null) return true;
      var service = getOsForwardingClient(region).networking().floatingip().portForwarding();
      try {
        PortForwarding rule = service.get(floatingIpId, ruleId);
        if (rule == null) return true;
        if (!marker.equals(rule.getDescription())) {
          throw new OpenStackOperationException("Refusing to delete an unowned forwarding rule", null);
        }
        requireDeleteResponse(service.delete(floatingIpId, ruleId));
        return service.get(floatingIpId, ruleId) == null;
      } catch (ResponseException error) {
        if (error.getStatus() == 404) return true;
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<Boolean> deleteManagedSecurityRule(String region, String projectId,
      String ruleId, String marker) {
    return CompletableFuture.supplyAsync(() -> {
      if (ruleId == null) return true;
      var service = getOsAdminClient(region, projectId).networking().securityrule();
      try {
        SecurityGroupRule rule = service.get(ruleId);
        if (rule == null) return true;
        if (!marker.equals(rule.getDescription())) {
          throw new OpenStackOperationException("Refusing to delete an unowned security rule", null);
        }
        service.delete(ruleId);
        return service.get(ruleId) == null;
      } catch (ResponseException error) {
        if (error.getStatus() == 404) return true;
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  private OSClient.OSClientV3 getOsForwardingClient(String region) {
    return getOsAdminClient(region, getOpenstackCluster(region).getAdminDefaultProjectId());
  }

  public CompletionStage<NetFloatingIP> getFloatingIp(String region, String floatingIpId) {
    return CompletableFuture.supplyAsync(
        () -> getOsForwardingClient(region).networking().floatingip().get(floatingIpId), NETWORK_EXECUTOR);
  }

  private static void requireDeleteResponse(ActionResponse response) {
    if (response == null || (!response.isSuccess() && response.getCode() != 404)) {
      throw new IllegalStateException("Neutron did not confirm deletion");
    }
  }

  private static boolean coversForwarding(SecurityGroupRule rule, String groupId, int port, NetProtocol protocol) {
    return groupId.equals(rule.getSecurityGroupId()) && "ingress".equalsIgnoreCase(rule.getDirection())
        && "IPv4".equalsIgnoreCase(rule.getEtherType()) && protocol.name().equalsIgnoreCase(rule.getProtocol())
        && rule.getRemoteGroupId() == null
        && (rule.getRemoteIpPrefix() == null || "0.0.0.0/0".equals(rule.getRemoteIpPrefix()))
        && (rule.getPortRangeMin() == null || rule.getPortRangeMin() <= port)
        && (rule.getPortRangeMax() == null || rule.getPortRangeMax() >= port);
  }

  public void deleteServer(String region, String projectId, String serverId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      client.compute().servers().delete(serverId);
    } catch (Exception e) {
      log.error("Failed to delete server: " + serverId, e);
    }
  }

  public void disassociateFloatIP(String region, String projectId, String fipId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      client.networking().floatingip().disassociateFromPort(fipId);
    } catch (Exception e) {
      log.error("Failed to disassociate floatip: " + fipId, e);
    }
  }

  public void deleteFloatIP(String region, String projectId, String fipId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      client.networking().floatingip().delete(fipId);
    } catch (Exception e) {
      log.error("Failed to delete floatip: " + fipId, e);
    }
  }

  public void deletePort(String region, String projectId, String portId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      client.networking().port().delete(portId);
    } catch (Exception e) {
      log.error("Failed to delete port: " + portId, e);
    }
  }

  public void deleteTrunk(String region, String projectId, String trunkId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      client.networking().trunk().delete(trunkId);
    } catch (Exception e) {
      log.error("Failed to delete trunk: " + trunkId, e);
    }
  }

  public Router createRouter(String regionId, String projectId, String name, String extNetId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    return client
        .networking()
        .router()
        .create(Builders.router().name(name).tenantId(projectId)
            .adminStateUp(true).externalGateway(extNetId).build());
  }

  public Network createNetwork(String regionId, String projectId, String name) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    return client
        .networking()
        .network()
        .create(Builders.network().name(name).tenantId(projectId).adminStateUp(true)
            .isShared(false).isRouterExternal(false).build());
  }

  public Network createNetwork(
      String regionId,
      String projectId,
      String name,
      NetworkType networkType,
      boolean isRouterExternal,
      boolean isShared,
      String physicalNetwork,
      String segmentId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    NetworkBuilder networkBuilder =
        Builders.network()
            .name(name)
            .adminStateUp(true)
            .networkType(networkType)
            .isShared(isShared)
            .isRouterExternal(isRouterExternal);
    if (EnumSet.of(NetworkType.VLAN, NetworkType.FLAT).contains(networkType)) {
      networkBuilder.physicalNetwork(physicalNetwork);
    }
    if (EnumSet.of(NetworkType.VLAN, NetworkType.VXLAN, NetworkType.GENEVE).contains(networkType)) {
      networkBuilder.segmentId(segmentId);
    }
    return client.networking().network().create(networkBuilder.build());
  }

  public Subnet createSubnet(
      String regionId,
      String projectId,
      String networkId,
      String name,
      String ipv4CIDR,
      String dns,
      String gateway,
      Map<String, String> hostRoute) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    SubnetBuilder subnetBuilder =
        Builders.subnet()
            .name(name)
            .tenantId(projectId)
            .ipVersion(IPVersionType.V4)
            .cidr(ipv4CIDR)
            .networkId(networkId)
            .gateway(gateway)
            .enableDHCP(true);
    if (dns != null && !dns.isBlank()) {
      Arrays.stream(dns.split(","))
          .map(String::trim)
          .filter(value -> !value.isBlank())
          .forEach(subnetBuilder::addDNSNameServer);
    }
    hostRoute.forEach(subnetBuilder::addHostRoute);
    return client.networking().subnet().create(subnetBuilder.build());
  }

  public RouterInterface attachRouterInterface(
      String regionId, String projectId, String routerId, String subnetId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    return client
        .networking()
        .router()
        .attachInterface(routerId, AttachInterfaceType.SUBNET, subnetId);
  }

  public Network getNetwork(String regionId, String projectId, String networkId) {
    return getOsAdminClient(regionId, projectId).networking().network().get(networkId);
  }

  public Subnet getSubnet(String regionId, String projectId, String subnetId) {
    return getOsAdminClient(regionId, projectId).networking().subnet().get(subnetId);
  }

  public List<? extends Network> listNetworks(String regionId, String projectId) {
    return getOsAdminClient(regionId, projectId).networking().network().list();
  }

  public List<? extends Subnet> listSubnets(String regionId, String projectId) {
    return getOsAdminClient(regionId, projectId).networking().subnet().list();
  }

  public List<? extends Router> listRouters(String regionId, String projectId) {
    return getOsAdminClient(regionId, projectId).networking().router().list();
  }

  public List<? extends Port> listPorts(String regionId, String projectId) {
    return getOsAdminClient(regionId, projectId).networking().port().list();
  }

  public Port createPort(String region, String projectId, String netId, String subnetId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .port()
        .create(Builders.port().fixedIp(null, subnetId).networkId(netId).build());
  }

  /** Lookup is always performed before creation; uncertain writes are subsequently lookup-only. */
  public CompletionStage<PreparedNetwork> ensureInstanceNetworkStep(ServerTarget target,
      String operationId, NetworkRequirement requirement, NetworkPreparation.Step step,
      PreparedNetwork prepared, boolean allowCreate) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return ensureInstanceNetworkStep(target, operationId, requirement, step, prepared, allowCreate,
            getOsAdminClient(resolveServerRegion(target), target.projectId()));
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance network preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  private PreparedNetwork ensureInstanceNetworkStep(ServerTarget target, String operationId,
      NetworkRequirement requirement, NetworkPreparation.Step step, PreparedNetwork prepared,
      boolean allowCreate, OSClient.OSClientV3 client) {
    String marker = instanceNetworkMarker(operationId, step);
    if (step == NetworkPreparation.Step.TRUNK) {
      List<? extends Trunk> matches = client.networking().trunk().list().stream()
          .filter(trunk -> marker.equals(trunk.getDescription())
              && target.projectId().equals(trunk.getTenantId())).toList();
      if (matches.size() > 1) throw new IllegalStateException("Duplicate instance trunk marker");
      Trunk trunk = matches.isEmpty() ? null : matches.getFirst();
      if (trunk == null && allowCreate) {
        NeutronTrunkSubport subport = NeutronTrunkSubport.fromTrunkSubport(NeutronTrunkSubport.builder()
            .portId(prepared.subPortId()).segmentationType("vlan")
            .segmentationId(requirement.segmentationId()).build());
        trunk = client.networking().trunk().create(Builders.neutron().trunk()
            .name(marker).description(marker).tenantId(target.projectId()).adminState(true)
            .parentPort(prepared.parentPortId()).trunkSubports(List.of(subport)).build());
        if (trunk == null) throw new IllegalStateException("Neutron returned no trunk");
      }
      if (trunk == null) return null;
      if (!prepared.parentPortId().equals(trunk.getParentPort())
          || trunk.getTrunkSubports() == null || trunk.getTrunkSubports().size() != 1
          || trunk.getTrunkSubports().stream().noneMatch(sub -> prepared.subPortId().equals(sub.getPortId())
              && "vlan".equals(sub.getSegmentationType())
              && requirement.segmentationId() == sub.getSegmentationId())) {
        throw new OpenStackOperationException("Instance trunk has mismatched ports or VLAN", null);
      }
      requireServerText(trunk.getId(), "trunkId");
      return new PreparedNetwork(prepared.parentPortId(), prepared.parentMacAddress(),
          prepared.parentFixedIp(), prepared.subPortId(), prepared.subFixedIp(), trunk.getId());
    }
    if (step == NetworkPreparation.Step.COMPLETE) return prepared;
    boolean parent = step == NetworkPreparation.Step.PARENT_PORT;
    String networkId = parent ? requirement.parentNetworkId() : requirement.subNetworkId();
    String subnetId = parent ? requirement.parentSubnetId() : requirement.subSubnetId();
    List<? extends Port> matches = client.networking().port().list(
        PortListOptions.create().tenantId(target.projectId()).name(marker)).stream()
        .filter(port -> marker.equals(port.getName()) && target.projectId().equals(port.getTenantId()))
        .toList();
    if (matches.size() > 1) throw new IllegalStateException("Duplicate instance port marker");
    Port port = matches.isEmpty() ? null : matches.getFirst();
    if (port == null && allowCreate) {
      var builder = Builders.port().name(marker).tenantId(target.projectId())
          .networkId(networkId).fixedIp(null, subnetId).adminState(true);
      if (!parent) builder.macAddress(prepared.parentMacAddress());
      port = client.networking().port().create(builder.build());
      if (port == null) throw new IllegalStateException("Neutron returned no port");
    }
    if (port == null) return null;
    if (!networkId.equals(port.getNetworkId()) || port.getFixedIps() == null
        || (!parent && !prepared.parentMacAddress().equalsIgnoreCase(port.getMacAddress()))) {
      throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
    }
    requireServerText(port.getId(), "portId");
    requireServerText(port.getMacAddress(), "portMacAddress");
    String fixedIp = port.getFixedIps().stream().filter(ip -> subnetId.equals(ip.getSubnetId()))
        .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
        .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
    return parent
        ? new PreparedNetwork(port.getId(), port.getMacAddress(), fixedIp, null, null, null)
        : new PreparedNetwork(prepared.parentPortId(), prepared.parentMacAddress(),
            prepared.parentFixedIp(), port.getId(), fixedIp, null);
  }

  /** Discover by operation marker as IDs may have been lost with a create response. */
  public CompletionStage<Void> deleteInstanceNetwork(ServerTarget target, String operationId) {
    return CompletableFuture.runAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.projectId());
      String trunkMarker = instanceNetworkMarker(operationId, NetworkPreparation.Step.TRUNK);
      for (Trunk trunk : client.networking().trunk().list()) {
        if (!trunkMarker.equals(trunk.getDescription()) || !target.projectId().equals(trunk.getTenantId())) continue;
        if (trunk.getTrunkSubports() != null) {
          for (TrunkSubport sub : trunk.getTrunkSubports()) {
            client.networking().trunk().removeTrunkSubport(trunk.getId(), sub.getPortId());
          }
        }
        requireDeleteResponse(client.networking().trunk().delete(trunk.getId()));
      }
      for (NetworkPreparation.Step step : List.of(NetworkPreparation.Step.SUB_PORT, NetworkPreparation.Step.PARENT_PORT)) {
        String marker = instanceNetworkMarker(operationId, step);
        for (Port port : client.networking().port().list(
            PortListOptions.create().tenantId(target.projectId()).name(marker))) {
          if (marker.equals(port.getName()) && target.projectId().equals(port.getTenantId())) {
            requireDeleteResponse(client.networking().port().delete(port.getId()));
          }
        }
      }
    }, NETWORK_EXECUTOR);
  }

  private static String instanceNetworkMarker(String operationId, NetworkPreparation.Step step) {
    return "arcreactor-" + UUID.nameUUIDFromBytes(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        + "-" + step.name().toLowerCase(Locale.ROOT);
  }

  public Port createSubPort(
      String region, String projectId, String netId, String subnetId, String macAddress) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .port()
        .create(
            Builders.port()
                .fixedIp(null, subnetId)
                .networkId(netId)
                .macAddress(macAddress)
                .build());
  }

  public Trunk createTrunk(String region, String projectId, String name, String parentPortId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .trunk()
        .create(
            Builders.neutron()
                .trunk()
                .name(name)
                .adminState(true)
                .parentPort(parentPortId)
                .build());
  }

  public NeutronNetQosPolicy createNetworkQosPolicy(
      String region, String projectId, String name, int maxKbps, int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    NeutronNetQosPolicy netQosPolicy =
        (NeutronNetQosPolicy)
            client
                .networking()
                .netQosPolicy()
                .create(NeutronNetQosPolicy.builder().name(name).build());
    client
        .networking()
        .netQosPolicyBandwidthLimitRule()
        .create(
            netQosPolicy.getId(),
            NeutronNetQosPolicyBandwidthLimitRule.builder()
                .direction(NetQosPolicyBandwidthLimitRule.Direction.ingress)
                .maxKbps(maxKbps)
                .maxBurstKbps(maxBurstKbits)
                .build());
    client
        .networking()
        .netQosPolicyBandwidthLimitRule()
        .create(
            netQosPolicy.getId(),
            NeutronNetQosPolicyBandwidthLimitRule.builder()
                .direction(NetQosPolicyBandwidthLimitRule.Direction.egress)
                .maxKbps(maxKbps)
                .maxBurstKbps(maxBurstKbits)
                .build());
    return netQosPolicy;
  }

  public NeutronNetQosPolicy createNetworkQosPolicy(String region, String projectId, String name) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    NeutronNetQosPolicy netQosPolicy =
        (NeutronNetQosPolicy)
            client
                .networking()
                .netQosPolicy()
                .create(NeutronNetQosPolicy.builder().name(name).build());
    return netQosPolicy;
  }

  public NetQosPolicyBandwidthLimitRule createNetworkQosBandwidthLimitRule(
      String region,
      String projectId,
      String policyId,
      NetQosPolicyBandwidthLimitRule.Direction direction,
      int maxKbps,
      int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .netQosPolicyBandwidthLimitRule()
        .create(
            policyId,
            NeutronNetQosPolicyBandwidthLimitRule.builder()
                .direction(direction)
                .maxKbps(maxKbps)
                .maxBurstKbps(maxBurstKbits)
                .build());
  }

  public void deleteNetworkQosPolicy(String region, String projectId, String qosPolicyId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    client.networking().netQosPolicy().delete(qosPolicyId);
  }

  public void updateNetworkQosPolicy(
      String region,
      String projectId,
      String qosPolicyId,
      String ruleId,
      int maxKbps,
      int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    NetQosPolicyBandwidthLimitRule rule =
        NeutronNetQosPolicyBandwidthLimitRule.builder()
            .maxKbps(maxKbps)
            .maxBurstKbps(maxBurstKbits)
            .build();
    rule.setId(ruleId);
    client.networking().netQosPolicyBandwidthLimitRule().update(qosPolicyId, rule);
  }

  public void attachQosPolicyToPort(
      String region, String projectId, String portId, String qosPolicyId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    Port port = Builders.port().qosPolicyId(qosPolicyId).build();
    port.setId(portId);
    client.networking().port().update(port);
  }

  public void attachTrunkSubport(
      String region, String projectId, String trunkId, String subportId, int segmentId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    client
        .networking()
        .trunk()
        .addTrunkSubport(
            trunkId,
            NeutronTrunkSubport.builder()
                .portId(subportId)
                .segmentationId(segmentId)
                .segmentationType("vlan")
                .build());
  }

  public NetFloatingIP createPortForwardingFloatingIP(
      String region, String projectId, String extNetId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .floatingip()
        .create(Builders.netFloatingIP().floatingNetworkId(extNetId).build());
  }

  public NetFloatingIP createFloatingIP(
      String region, String projectId, String portId, String extNetId) {

    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .floatingip()
        .create(Builders.netFloatingIP().portId(portId).floatingNetworkId(extNetId).build());
  }

  public void rebootHard(String region, String projectId, String serverId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    client.compute().servers().reboot(serverId, RebootType.HARD);
  }

  public void rebuild(String region, String projectId, String serverId, String imageId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    client.compute().servers().rebuild(serverId, RebuildOptions.create().image(imageId));
  }

  public Server createVM(
      String region,
      String projectId,
      String name,
      String flavorId,
      String imageId,
      String portId,
      String userData,
      String hostname) {
    ServerCreate sc =
        Builders.server()
            .name(name)
            .flavor(flavorId)
            .image(imageId)
            .configDrive(true)
            .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
            .addNetworkPort(portId)
            .hypervisorHostName(hostname)
            .addSecurityGroup("default")
            .build();

    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    if (client instanceof OSClientSession.OSClientSessionV3 clientSessionV3) {
      HashMap<String, String> map = new HashMap<>();
      map.put("OpenStack-API-Version", "compute 2.100");
      clientSessionV3.headers(map);
    }
    return client.compute().servers().boot(sc);
  }

  public Server getServer(String regionId, String projectId, String vmId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    return client.compute().servers().get(vmId);
  }

  /** Runs blocking SDK calls on virtual threads so instance actors can await their results. */
  public CompletionStage<NovaServer> createServer(CreateServerRequest request) {
    return CompletableFuture.supplyAsync(
        () -> {
          String region = resolveServerRegion(request.target());
          var hypervisor = hypervisorHostRepo.findById(request.hypervisorId());
          if (hypervisor == null
              || !region.equals(hypervisor.getRegionId())
              || hypervisor.getHostname() == null
              || hypervisor.getHostname().isBlank()) {
            throw new OpenStackOperationException(
                "Selected hypervisor has no hostname in the target region", null);
          }
          requireServerText(request.operationId(), "operationId");
          Map<String, String> metadata = new HashMap<>(request.metadata());
          metadata.put(NOVA_OPERATION_ID, request.operationId());
          metadata.put("arcreactor_instance_id", request.instanceId());
          metadata.put("arcreactor_request_id", request.requestId());
          var builder =
              Builders.server()
                  .name(request.name())
                  .image(request.imageId())
                  .flavor(request.flavorId())
                  .hypervisorHostName(hypervisor.getHostname())
                  .addMetadata(metadata);
          if (request.networkPortId() != null && !request.networkPortId().isBlank()) {
            builder.addNetworkPort(request.networkPortId());
          }
          if (request.userData() != null) {
            builder.configDrive(true).userData(Base64.getEncoder()
                .encodeToString(request.userData().getBytes(StandardCharsets.UTF_8)));
          }
          try {
            OSClient.OSClientV3 client = getOsAdminClient(region, request.target().projectId());
            if (client instanceof OSClientSession.OSClientSessionV3 session) {
              session.headers(Map.of("OpenStack-API-Version", "compute 2.100"));
            }
            return toNovaServer(client.compute().servers().boot(builder.build()));
          } catch (ResponseException error) {
            int status = error.getStatus();
            // Timeouts, rate limits and server errors leave the outcome uncertain for reconciliation.
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
              throw new OpenStackOperationException("Nova rejected server creation", error);
            }
            throw error;
          }
        },
        NOVA_EXECUTOR);
  }

  public CompletionStage<NovaServer> getServer(ServerTarget target, String serverId) {
    return CompletableFuture.supplyAsync(
        () -> toNovaServer(getServer(resolveServerRegion(target), target.projectId(), serverId)),
        NOVA_EXECUTOR);
  }

  public CompletionStage<ServerLookupResult> findServerByOperationId(
      ServerTarget target, String operationId) {
    return CompletableFuture.supplyAsync(
        () -> {
          requireServerText(operationId, "operationId");
          OSClient.OSClientV3 client =
              getOsAdminClient(resolveServerRegion(target), target.projectId());
          Map<String, String> filters = new HashMap<>();
          filters.put("limit", "1000");
          NovaServer found = null;
          Set<String> markers = new HashSet<>();
          while (true) {
            List<? extends Server> page = client.compute().servers().list(filters);
            if (page == null) throw new IllegalStateException("Nova returned no server list");
            if (page.isEmpty()) break;
            for (Server server : page) {
              if (target.projectId().equals(server.getTenantId())
                  && server.getMetadata() != null
                  && operationId.equals(server.getMetadata().get(NOVA_OPERATION_ID))) {
                if (found != null) {
                  throw new IllegalStateException(
                      "Multiple Nova servers share operation " + operationId);
                }
                found = toNovaServer(server);
              }
            }
            String marker = page.get(page.size() - 1).getId();
            if (marker == null || !markers.add(marker)) {
              throw new IllegalStateException("Nova server pagination did not advance");
            }
            filters.put("marker", marker);
          }
          return new ServerLookupResult(
              found == null ? ServerLookupResult.Status.NOT_FOUND : ServerLookupResult.Status.FOUND,
              found);
        },
        NOVA_EXECUTOR);
  }

  public CompletionStage<Void> deleteServer(ServerTarget target, String serverId) {
    return CompletableFuture.runAsync(
        () -> {
          OSClient.OSClientV3 client =
              getOsAdminClient(resolveServerRegion(target), target.projectId());
          try {
            ActionResponse response = client.compute().servers().delete(serverId);
            if (response == null) throw new IllegalStateException("Nova returned no delete response");
            if (!response.isSuccess() && response.getCode() != 404) {
              throw new OpenStackOperationException(
                  "Nova server deletion failed: " + response.getFault(), null);
            }
          } catch (ResponseException error) {
            if (error.getStatus() != 404) throw error;
          }
        },
        NOVA_EXECUTOR);
  }

  private String resolveServerRegion(ServerTarget target) {
    requireServerText(target.clusterId(), "clusterId");
    requireServerText(target.projectId(), "projectId");
    if (target.region() != null && !target.region().isBlank()) {
      RegionEntity region = regionRepo.findById(target.region());
      if (region == null || !target.clusterId().equals(region.getClusterId())) {
        throw new OpenStackOperationException("Region does not belong to the target cluster", null);
      }
      return region.getId();
    }
    List<RegionEntity> regions = regionRepo.findByClusterId(target.clusterId());
    if (regions.size() != 1) {
      throw new OpenStackOperationException("Specify a region for the target cluster", null);
    }
    return regions.getFirst().getId();
  }

  private static void requireServerText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new OpenStackOperationException(field + " is required", null);
    }
  }

  private static NovaServer toNovaServer(Server server) {
    if (server == null || server.getId() == null || server.getId().isBlank()) {
      throw new IllegalStateException("Nova returned no server ID");
    }
    NovaServer.Status status = NovaServer.Status.OTHER;
    if (server.getStatus() != null) {
      status = switch (server.getStatus()) {
        case BUILD -> NovaServer.Status.BUILD;
        case ACTIVE -> NovaServer.Status.ACTIVE;
        case ERROR -> NovaServer.Status.ERROR;
        default -> NovaServer.Status.OTHER;
      };
    }
    return new NovaServer(
        server.getId(),
        status,
        server.getFault() == null ? null : server.getFault().getMessage());
  }

  public String getVNCLink(String region, String projectId, String serverId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
      return client.compute().servers().getVNCConsole(serverId, VNCConsole.Type.NOVNC).getURL();
    } catch (Exception e) {
      return null;
    }
  }

  public Flavor createFlavor(String region, String name, int vcpus, int ram, int disk) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    return osClient
        .compute()
        .flavors()
        .create(Builders.flavor().name(name).vcpus(vcpus).ram(ram * 1024).disk(disk).build());
  }

  public void deleteFlavor(String region, String projectId, String flavorId) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    osClient.compute().flavors().delete(flavorId);
  }

  public void updateFlavorMetaData(
      String region, String flavorId, Map<String, String> flavorMetaData) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    osClient.compute().flavors().createAndUpdateExtraSpecs(flavorId, flavorMetaData);
  }

  /** Runs blocking Manila SDK calls, including authentication, on virtual threads. */
  public CompletionStage<Share> createShare(String region, String projectId, ShareCreate shareCreate) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return getOsShareClient(region, projectId).share().shares().create(shareCreate);
      } catch (Exception e) {
        log.error("create share failed", e);
        throw new OpenStackOperationException("create share failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Share> getShare(String region, String projectId, String shareId) {
    return CompletableFuture.supplyAsync(
        () -> getOsShareClient(region, projectId).share().shares().get(shareId), SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends Share>> listShares(String region, String projectId) {
    return CompletableFuture.supplyAsync(
        () -> getOsShareClient(region, projectId).share().shares().listDetails(), SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends Access>> listShareAccess(
      String region, String projectId, String shareId) {
    return CompletableFuture.supplyAsync(
        () -> getOsShareClient(region, projectId).share().shares().listAccess(shareId), SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends ShareExportLocation>> getShareExportLocation(
      String region, String projectId, String shareId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return getOsShareClient(region, projectId).share().shares().listExportLocations(shareId);
      } catch (Exception e) {
        log.error("get share export location failed", e);
        throw new OpenStackOperationException("get share export location failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Access> grantShareAccess(
      String region, String projectId, String shareId, AccessOptions accessOptions) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return getOsShareClient(region, projectId)
            .share()
            .shares()
            .grantAccess(shareId, accessOptions);
      } catch (Exception e) {
        log.error("grant share access failed", e);
        throw new OpenStackOperationException("grant share access failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Access> getShareAccess(String region, String projectId, String accessId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return getOsShareClient(region, projectId).share().shares().getAccess(accessId);
      } catch (Exception e) {
        log.error("get share access failed", e);
        throw new OpenStackOperationException("get share access failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  private OSClient.OSClientV3 getOsShareClient(String region, String projectId) {
    return getOsAdminClient(region, projectId).perspective(Facing.INTERNAL);
  }
}
