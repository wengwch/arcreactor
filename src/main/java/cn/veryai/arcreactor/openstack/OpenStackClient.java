package cn.veryai.arcreactor.openstack;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingResources;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedPortQos;
import cn.veryai.arcreactor.actor.user.model.NetworkResources;
import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.ResponseException;
import org.openstack4j.api.types.Facing;
import org.openstack4j.api.networking.ext.PortForwardingService;
import org.openstack4j.api.manila.SharesService;
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
import org.openstack4j.model.network.ext.NetQosPolicy;
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

  public static final String SHARE_OPERATION_ID = "veryai_share_operation_id";

  private static final long TOKEN_REFRESH_MARGIN_MILLIS = Duration.ofMinutes(5).toMillis();
  private static final String NOVA_OPERATION_ID = "veryai_operation_id";
  private static final Executor NOVA_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-nova").start(task);
  private static final Executor SHARE_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-share").start(task);
  private static final Executor PROJECT_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-project").start(task);
  private static final Executor NETWORK_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-network").start(task);

  public record ManagedNetworkResource(String osResourceId) implements Serializable {
    public ManagedNetworkResource {
      if (osResourceId == null || osResourceId.isBlank()) throw new IllegalStateException("Missing network resource ID");
    }
  }
  public record ServerTarget(String clusterId, String osProjectId, String region)
      implements Serializable {}

  public record CreateServerRequest(
      ServerTarget target,
      String instanceId,
      String requestId,
      String operationId,
      String name,
      String osImageId,
      String osFlavorId,
      String hypervisorId,
      Map<String, String> metadata,
      String osNetworkPortId,
      String userData) implements Serializable {
    public CreateServerRequest {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    public CreateServerRequest(ServerTarget target, String instanceId, String requestId,
        String operationId, String name, String osImageId, String osFlavorId, String hypervisorId,
        Map<String, String> metadata, String osNetworkPortId) {
      this(target, instanceId, requestId, operationId, name, osImageId, osFlavorId, hypervisorId,
          metadata, osNetworkPortId, null);
    }
    public CreateServerRequest(ServerTarget target, String instanceId, String requestId,
        String operationId, String name, String osImageId, String osFlavorId, String hypervisorId,
        Map<String, String> metadata) {
      this(target, instanceId, requestId, operationId, name, osImageId, osFlavorId, hypervisorId, metadata, null);
    }

    @Override
    public String toString() {
      return "CreateServerRequest[instanceId=" + instanceId + ", operationId=" + operationId
          + ", userData=<redacted>]";
    }
  }

  public record NovaServer(String osServerId, Status status, String fault) implements Serializable {
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

  private record AdminClientKey(String clusterId, String osProjectId) {}

  @Autowired private OpenstackClusterRepo clusterRepo;

  @Autowired private RegionRepo regionRepo;

  @Autowired private HypervisorHostRepo hypervisorHostRepo;

  @Autowired private SysNetworkRepo sysNetworkRepo;

  public OSClient.OSClientV3 getOsAdminClient(String region, String osProjectId) {
    return getOsClusterAdminClient(getOpenstackCluster(region), osProjectId).useRegion(region);
  }

  private OSClient.OSClientV3 getOsClusterAdminClient(OpenstackClusterEntity cluster, String osProjectId) {
    requireServerText(osProjectId, "osProjectId");
    Token token =
        adminTokenCache
            .asMap()
            .compute(
                new AdminClientKey(cluster.getId(), osProjectId),
                (key, cachedToken) -> {
                  if (cachedToken != null
                      && cachedToken.getExpires() != null
                      && cachedToken.getExpires().getTime()
                          > System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MILLIS) {
                    return cachedToken;
                  }
                  return authenticateAdmin(cluster, key.osProjectId());
                });
    // Sessions have mutable headers/perspective and thread-local state; only share the token.
    return OSFactory.clientFromToken(token).useRegion(cluster.getOsAdminDefaultRegion());
  }

  private Token authenticateAdmin(OpenstackClusterEntity cluster, String osProjectId) {
    return OSFactory.builderV3()
        .endpoint(cluster.getAuthEndpoint())
        .credentials(
            cluster.getAdminUsername(),
            cluster.getAdminPassword(),
            Identifier.byId(cluster.getOsAdminDefaultDomainId()))
        .scopeToProject(Identifier.byId(osProjectId))
        .authenticate()
        .getToken();
  }

  public OpenstackClusterEntity getOpenstackCluster(String region) {
    RegionEntity regionEntity = regionRepo.findById(region);
    return clusterRepo.findById(regionEntity.getClusterId());
  }

  public List<? extends Hypervisor> listHypervisors(String region) {
    // Hypervisors are cloud-wide resources without a project_id filter.
    return getOsHypervisorClient(region).compute().hypervisors().list();
  }

  public Hypervisor getHypervisor(String region, String osHypervisorId) {
    OSClient.OSClientV3 osClient = getOsHypervisorClient(region);
    try {
      return osClient.compute().hypervisors().get(osHypervisorId);
    } catch (ResponseException exception) {
      if (exception.getStatus() == 404) return null;
      throw exception;
    }
  }

  private OSClient.OSClientV3 getOsHypervisorClient(String region) {
    OpenstackClusterEntity cluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getOsAdminDefaultProjectId());
    if (osClient instanceof OSClientSession.OSClientSessionV3 clientSessionV3) {
      HashMap<String, String> map = new HashMap<>();
      map.put("OpenStack-API-Version", "compute 2.87");
      clientSessionV3.headers(map);
    }
    return osClient;
  }

  public CompletionStage<Void> grantProjectAdminRole(String clusterId, String osProjectId) {
    return CompletableFuture.runAsync(() -> {
      OpenstackClusterEntity cluster = clusterRepo.findById(clusterId);
      requireServerText(cluster.getOsAdminRoleId(), "osAdminRoleId");
      // Authenticate in the existing admin project before granting access to the new project.
      OSClient.OSClientV3 osClient = getOsClusterAdminClient(cluster, cluster.getOsAdminDefaultProjectId());
      ActionResponse response = osClient.identity().roles().grantProjectUserRole(
          osProjectId, osClient.getToken().getUser().getId(), cluster.getOsAdminRoleId());
      if (response == null || !response.isSuccess()) {
        throw new IllegalStateException("Project administrator role assignment was not confirmed");
      }
    }, PROJECT_EXECUTOR);
  }

  /** Reuses a project with the same name in the configured domain, creating it only when absent. */
  public CompletionStage<Project> createProject(String clusterId, String name, String operationId) {
    return CompletableFuture.supplyAsync(() -> {
      OpenstackClusterEntity cluster = clusterRepo.findById(clusterId);
      OSClient.OSClientV3 osClient = getOsClusterAdminClient(cluster, cluster.getOsAdminDefaultProjectId());
      String osDomainId = cluster.getOsAdminDefaultDomainId();
      var projects = osClient.identity().projects();
      Project existing = projects.getByName(name, osDomainId);
      if (existing != null) return existing;
      return projects.create(Builders.project()
          .domainId(osDomainId).name(name).enabled(true)
          .description(operationId == null ? null : projectOperationMarker(operationId)).build());
    }, PROJECT_EXECUTOR);
  }

  /** Reuses or creates the project, then grants its administrator role once. */
  public CompletionStage<Project> createProjectWithAdminRole(String clusterId, String name,
      String operationId) {
    requireServerText(operationId, "operationId");
    return createProject(clusterId, name, operationId).thenCompose(project -> {
      if (project == null || project.getId() == null || project.getId().isBlank()) {
        throw new IllegalStateException("Keystone returned no project ID");
      }
      return grantProjectAdminRole(clusterId, project.getId()).thenApply(ignored -> project);
    });
  }

  private static String projectOperationMarker(String operationId) {
    return "veryai:" + operationId;
  }

  public void updateProject(String region, String osProjectId, String name) {
    OSClient.OSClientV3 osClient = getOsAdminClient(region, osProjectId);
    osClient.identity().projects().update(Builders.project().id(osProjectId).name(name).build());
  }

  public SecurityGroup getSecurityGroup(String region, String osProjectId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    return client.networking().securitygroup().list(Map.of("project_id", osProjectId)).stream()
        .filter(s -> s.getName().equalsIgnoreCase("default"))
        .findFirst()
        .orElse(null);
  }

  public void deleteSecurityGroupRule(String region, String osProjectId, String osSecurityGroupRuleId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    client.networking().securityrule().delete(osSecurityGroupRuleId);
  }

  public SecurityGroupRule addSecurityGroupRule(
      String region, String osProjectId, String osSecurityGroupId, int port, NetProtocol protocol) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    SecurityGroupRule sgr =
        client
            .networking()
            .securityrule()
            .list(Map.of("project_id", osProjectId, "security_group_id", osSecurityGroupId))
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
                .securityGroupId(osSecurityGroupId)
                .protocol(protocol.name().toLowerCase())
                .direction("ingress")
                .ethertype("IPv4")
                .portRangeMin(port)
                .portRangeMax(port)
                .remoteIpPrefix("0.0.0.0/0")
                .build());
  }

  /** Reads existing rule IDs and external ports for floating IP activation. */
  public CompletionStage<List<PortForwardingSnapshot>> listPortForwardings(
      String region, String osProjectId, String osFloatingIpId) {
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      requireProjectFloatingIp(client, osProjectId, osFloatingIpId);
      return client.networking().floatingip().portForwarding().list(osFloatingIpId).stream()
          .map(rule -> new PortForwardingSnapshot(rule.getId(), rule.getExternalPort())).toList();
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<Void> deletePortForwarding(String region, String osFloatingIpId, String osForwardingId) {
    return CompletableFuture.runAsync(() -> {
      try {
        ActionResponse response = getOsForwardingClient(region).networking().floatingip()
            .portForwarding().delete(osFloatingIpId, osForwardingId);
        requireDeleteResponse(response);
      } catch (ResponseException error) {
        if (error.getStatus() != 404) throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  public CompletionStage<PortForwarding> createPortForwarding(
      String region,
      String osFloatingIpId,
      int externalPort,
      String fixedIp,
      int internalPort,
      String osInternalPortId,
      NetProtocol protocol) {

    return CompletableFuture.supplyAsync(() -> getOsForwardingClient(region).networking()
        .floatingip().portForwarding().create(osFloatingIpId, Builders.portForwarding()
            .externalPort(externalPort).internalIpAddress(fixedIp).internalPort(internalPort)
            .internalPortId(osInternalPortId).protocol(protocol.name().toLowerCase(Locale.ROOT)).build()),
        NETWORK_EXECUTOR);
  }

  /** Directly creates TCP and UDP rules; markers are retained for creation recovery. */
  public CompletionStage<ForwardingResources> createPortForwardings(FloatingIpIdentity identity,
      String claimId, ForwardingSpec spec, int externalPort) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsAdminClient(identity.regionId(), identity.osProjectId()).networking().floatingip().portForwarding();
      String osTcpForwardingId = createPortForwarding(service, identity.osFloatingIpId(), externalPort, spec, NetProtocol.TCP,
          forwardingMarker(claimId, NetProtocol.TCP));
      String osUdpForwardingId = createPortForwarding(service, identity.osFloatingIpId(), externalPort, spec, NetProtocol.UDP,
          forwardingMarker(claimId, NetProtocol.UDP));
      return new ForwardingResources(identity.floatingIp(), externalPort, osTcpForwardingId, osUdpForwardingId);
    }, NETWORK_EXECUTOR);
  }

  /** Recovery reuses matching rules on the reserved port and creates only missing protocols. */
  public CompletionStage<ForwardingResources> recoverPortForwardings(FloatingIpIdentity identity,
      String claimId, ForwardingSpec spec, int externalPort) {
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(identity.regionId(), identity.osProjectId());
      requireProjectFloatingIp(client, identity.osProjectId(), identity.osFloatingIpId());
      var service = client.networking().floatingip().portForwarding();
      List<? extends PortForwarding> forwardings = service.list(identity.osFloatingIpId());
      String tcpMarker = forwardingMarker(claimId, NetProtocol.TCP);
      String udpMarker = forwardingMarker(claimId, NetProtocol.UDP);
      // Validate both protocols before creating anything during recovery.
      String osTcpForwardingId = findExistingForwardingId(forwardings, externalPort, spec, NetProtocol.TCP, tcpMarker);
      String osUdpForwardingId = findExistingForwardingId(forwardings, externalPort, spec, NetProtocol.UDP, udpMarker);
      if (osTcpForwardingId == null) {
        osTcpForwardingId = createPortForwarding(service, identity.osFloatingIpId(), externalPort, spec, NetProtocol.TCP, tcpMarker);
      }
      if (osUdpForwardingId == null) {
        osUdpForwardingId = createPortForwarding(service, identity.osFloatingIpId(), externalPort, spec, NetProtocol.UDP, udpMarker);
      }
      return new ForwardingResources(identity.floatingIp(), externalPort, osTcpForwardingId, osUdpForwardingId);
    }, NETWORK_EXECUTOR);
  }

  private static String findExistingForwardingId(List<? extends PortForwarding> forwardings,
      int externalPort, ForwardingSpec spec, NetProtocol protocol, String marker) {
    List<? extends PortForwarding> matches = forwardings.stream()
        .filter(rule -> marker.equals(rule.getDescription())).toList();
    if (matches.size() > 1) throw new IllegalStateException("Duplicate forwarding operation marker");
    if (matches.isEmpty()) return null;
    PortForwarding existing = matches.getFirst();
    requireForwardingMatches(existing, externalPort, spec, protocol, marker);
    return existing.getId();
  }

  private static void requireForwardingMatches(PortForwarding existing, int externalPort,
      ForwardingSpec spec, NetProtocol protocol, String marker) {
    if (!marker.equals(existing.getDescription()) || existing.getExternalPort() != externalPort
        || existing.getInternalPort() != spec.internalPort()
        || !spec.osInternalPortId().equals(existing.getInternalPortId())
        || !spec.fixedIp().equals(existing.getInternalIpAddress())
        || !protocol.name().equalsIgnoreCase(existing.getProtocol())) {
      throw new IllegalStateException("Forwarding operation marker has mismatched parameters");
    }
    if (existing.getId() == null || existing.getId().isBlank()) {
      throw new IllegalStateException("Missing forwarding ID");
    }
  }

  private String createPortForwarding(PortForwardingService service, String osFloatingIpId,
      int externalPort, ForwardingSpec spec, NetProtocol protocol, String marker) {
    PortForwarding created = service.create(osFloatingIpId, Builders.portForwarding()
        .externalPort(externalPort).internalIpAddress(spec.fixedIp()).internalPort(spec.internalPort())
        .internalPortId(spec.osInternalPortId()).protocol(protocol.name().toLowerCase(Locale.ROOT))
        .description(marker).build());
    if (created == null || created.getId() == null || created.getId().isBlank()) {
      throw new IllegalStateException("Missing forwarding ID");
    }
    return created.getId();
  }

  private static String forwardingMarker(String claimId, NetProtocol protocol) {
    String key = claimId + ":" + protocol;
    return "veryai:pf:" + key;
  }

  /** Creates the instance's own group and ingress rules before creating any ports. */
  public CompletionStage<String> prepareInstanceSecurityGroup(ServerTarget target, String instanceId,
      List<Integer> internalPorts) {
    List<Integer> ports = List.copyOf(internalPorts);
    if (ports.stream().anyMatch(port -> port < 1 || port > 65535)) {
      throw new IllegalArgumentException("Invalid instance security group port");
    }
    String marker = instanceSecurityGroupMarker(instanceId);
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
      SecurityGroup group = client.networking().securitygroup().create(Builders.securityGroup()
          .name(marker).description(marker).tenantId(target.osProjectId()).build());
      if (group == null || group.getId() == null || group.getId().isBlank()
          || !target.osProjectId().equals(group.getTenantId()) || !marker.equals(group.getDescription())) {
        throw new OpenStackOperationException("Missing or mismatched instance security group", null);
      }
      for (int port : ports) {
        for (NetProtocol protocol : List.of(NetProtocol.TCP, NetProtocol.UDP)) {
          createInstanceSecurityRule(client, group.getId(), port, protocol, marker);
        }
      }
      return group.getId();
    }, NETWORK_EXECUTOR);
  }

  /** Independently resumes the instance's group and missing rules after recovery. */
  public CompletionStage<String> recoverInstanceSecurityGroup(ServerTarget target, String instanceId,
      List<Integer> internalPorts) {
    List<Integer> ports = List.copyOf(internalPorts);
    if (ports.stream().anyMatch(port -> port < 1 || port > 65535)) {
      throw new IllegalArgumentException("Invalid instance security group port");
    }
    String marker = instanceSecurityGroupMarker(instanceId);
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
      List<? extends SecurityGroup> matches = findInstanceSecurityGroups(client, target.osProjectId(), marker);
      if (matches.size() > 1) {
        throw new OpenStackOperationException("Duplicate instance security group marker", null);
      }
      SecurityGroup group = matches.isEmpty() ? null : matches.getFirst();
      if (group == null) {
        group = client.networking().securitygroup().create(Builders.securityGroup()
            .name(marker).description(marker).tenantId(target.osProjectId()).build());
      }
      if (group == null || group.getId() == null || group.getId().isBlank()
          || !target.osProjectId().equals(group.getTenantId()) || !marker.equals(group.getDescription())) {
        throw new OpenStackOperationException("Missing or mismatched instance security group", null);
      }
      for (int port : ports) {
        for (NetProtocol protocol : List.of(NetProtocol.TCP, NetProtocol.UDP)) {
          ensureInstanceSecurityRule(client, target.osProjectId(), group.getId(), port, protocol, marker);
        }
      }
      return group.getId();
    }, NETWORK_EXECUTOR);
  }

  private void createInstanceSecurityRule(OSClient.OSClientV3 client, String osSecurityGroupId, int internalPort,
      NetProtocol protocol, String groupMarker) {
    SecurityGroupRule created = client.networking().securityrule().create(Builders.securityGroupRule()
        .securityGroupId(osSecurityGroupId).protocol(protocol.name().toLowerCase(Locale.ROOT)).direction("ingress")
        .ethertype("IPv4").portRangeMin(internalPort).portRangeMax(internalPort).remoteIpPrefix("0.0.0.0/0")
        .description(groupMarker + ":" + protocol.name().toLowerCase(Locale.ROOT) + ":" + internalPort).build());
    if (created == null || created.getId() == null || created.getId().isBlank()
        || !allowsInstancePort(created, osSecurityGroupId, internalPort, protocol)) {
      throw new OpenStackOperationException("Missing or mismatched instance security rule", null);
    }
  }

  private void ensureInstanceSecurityRule(OSClient.OSClientV3 client, String osProjectId, String osSecurityGroupId, int internalPort,
      NetProtocol protocol, String groupMarker) {
    var service = client.networking().securityrule();
    Map<String, String> filters = Map.of("project_id", osProjectId, "security_group_id", osSecurityGroupId);
    if (service.list(filters).stream()
        .anyMatch(rule -> allowsInstancePort(rule, osSecurityGroupId, internalPort, protocol))) return;
    try {
      createInstanceSecurityRule(client, osSecurityGroupId, internalPort, protocol, groupMarker);
    } catch (ResponseException error) {
      // An earlier request may have completed while recovery was querying the group's rules.
      if (error.getStatus() != 409 || service.list(filters).stream()
          .noneMatch(rule -> allowsInstancePort(rule, osSecurityGroupId, internalPort, protocol))) throw error;
    }
  }

  private List<? extends SecurityGroup> findInstanceSecurityGroups(OSClient.OSClientV3 client,
      String osProjectId, String marker) {
    return client.networking().securitygroup().list(Map.of("project_id", osProjectId)).stream()
        .filter(group -> osProjectId.equals(group.getTenantId()) && marker.equals(group.getDescription())).toList();
  }

  private static String instanceSecurityGroupMarker(String instanceId) {
    requireServerText(instanceId, "instanceId");
    return "veryai:sg:" + instanceId;
  }

  /** Called after instance ports have been removed. Also finds groups whose create response was lost. */
  public CompletionStage<Void> deleteInstanceSecurityGroup(ServerTarget target, String instanceId) {
    return CompletableFuture.runAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
      for (SecurityGroup group : findInstanceSecurityGroups(client, target.osProjectId(), instanceSecurityGroupMarker(instanceId))) {
        requireDeleteResponse(client.networking().securitygroup().delete(group.getId()));
      }
    }, NETWORK_EXECUTOR);
  }

  /** Queries recorded rules, resolving missing IDs by claim marker before deleting partial creations. */
  public CompletionStage<Boolean> deletePortForwardings(FloatingIpIdentity identity, String claimId,
      ForwardingSpec spec, ForwardingResources resources) {
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(identity.regionId(), identity.osProjectId());
      // A missing floating IP also has no forwarding rules; keep deletion idempotent.
      if (getProjectFloatingIp(client, identity.osProjectId(), identity.osFloatingIpId()) == null) return true;
      var service = client.networking().floatingip().portForwarding();
      String osTcpForwardingId = resources.osTcpForwardingId();
      String osUdpForwardingId = resources.osUdpForwardingId();
      String tcpMarker = forwardingMarker(claimId, NetProtocol.TCP);
      String udpMarker = forwardingMarker(claimId, NetProtocol.UDP);
      if (osTcpForwardingId == null || osTcpForwardingId.isBlank()
          || osUdpForwardingId == null || osUdpForwardingId.isBlank()) {
        List<? extends PortForwarding> forwardings = service.list(identity.osFloatingIpId());
        if (osTcpForwardingId == null || osTcpForwardingId.isBlank()) {
          osTcpForwardingId = findExistingForwardingId(forwardings, resources.externalPort(), spec, NetProtocol.TCP, tcpMarker);
        }
        if (osUdpForwardingId == null || osUdpForwardingId.isBlank()) {
          osUdpForwardingId = findExistingForwardingId(forwardings, resources.externalPort(), spec, NetProtocol.UDP, udpMarker);
        }
      }
      boolean tcpDeleted = deleteManagedPortForwarding(service, identity.osFloatingIpId(), osTcpForwardingId,
          resources.externalPort(), spec, NetProtocol.TCP, tcpMarker);
      boolean udpDeleted = deleteManagedPortForwarding(service, identity.osFloatingIpId(), osUdpForwardingId,
          resources.externalPort(), spec, NetProtocol.UDP, udpMarker);
      return tcpDeleted && udpDeleted;
    }, NETWORK_EXECUTOR);
  }

  /** Independently resumes deletion, discovering rules whose creation responses were not persisted. */
  public CompletionStage<Boolean> recoverPortForwardingDeletion(FloatingIpIdentity identity,
      String claimId, ForwardingSpec spec, ForwardingResources resources) {
    return CompletableFuture.supplyAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(identity.regionId(), identity.osProjectId());
      if (getProjectFloatingIp(client, identity.osProjectId(), identity.osFloatingIpId()) == null) return true;
      var service = client.networking().floatingip().portForwarding();
      String osTcpForwardingId = resources.osTcpForwardingId();
      String osUdpForwardingId = resources.osUdpForwardingId();
      if (osTcpForwardingId == null || osTcpForwardingId.isBlank()
          || osUdpForwardingId == null || osUdpForwardingId.isBlank()) {
        List<? extends PortForwarding> forwardings = service.list(identity.osFloatingIpId());
        if (osTcpForwardingId == null || osTcpForwardingId.isBlank()) {
          osTcpForwardingId = findExistingForwardingId(forwardings, resources.externalPort(), spec, NetProtocol.TCP,
              forwardingMarker(claimId, NetProtocol.TCP));
        }
        if (osUdpForwardingId == null || osUdpForwardingId.isBlank()) {
          osUdpForwardingId = findExistingForwardingId(forwardings, resources.externalPort(), spec, NetProtocol.UDP,
              forwardingMarker(claimId, NetProtocol.UDP));
        }
      }
      for (NetProtocol protocol : List.of(NetProtocol.TCP, NetProtocol.UDP)) {
        String osRuleId = protocol == NetProtocol.TCP ? osTcpForwardingId : osUdpForwardingId;
        if (osRuleId == null) continue;
        try {
          PortForwarding existing = service.get(identity.osFloatingIpId(), osRuleId);
          if (existing != null) {
            requireForwardingMatches(existing, resources.externalPort(), spec, protocol, forwardingMarker(claimId, protocol));
            requireDeleteResponse(service.delete(identity.osFloatingIpId(), osRuleId));
          }
        } catch (ResponseException error) {
          if (error.getStatus() != 404) throw error;
        }
      }
      return true;
    }, NETWORK_EXECUTOR);
  }

  private boolean deleteManagedPortForwarding(PortForwardingService service, String osFloatingIpId,
      String osRuleId, int externalPort, ForwardingSpec spec, NetProtocol protocol, String marker) {
    // Null is only passed after a successful marker lookup confirmed that the rule is absent.
    if (osRuleId == null) return true;
    try {
      PortForwarding existing = service.get(osFloatingIpId, osRuleId);
      if (existing == null) return true;
      requireForwardingMatches(existing, externalPort, spec, protocol, marker);
      requireDeleteResponse(service.delete(osFloatingIpId, osRuleId));
    } catch (ResponseException error) {
      if (error.getStatus() != 404) throw error;
    }
    return true;
  }

  private OSClient.OSClientV3 getOsForwardingClient(String region) {
    return getOsAdminClient(region, getOpenstackCluster(region).getOsAdminDefaultProjectId());
  }

  public CompletionStage<NetFloatingIP> getFloatingIp(String region, String osProjectId, String osFloatingIpId) {
    return CompletableFuture.supplyAsync(
        () -> getProjectFloatingIp(getOsAdminClient(region, osProjectId), osProjectId, osFloatingIpId), NETWORK_EXECUTOR);
  }

  private NetFloatingIP getProjectFloatingIp(OSClient.OSClientV3 client, String osProjectId, String osFloatingIpId) {
    try {
      NetFloatingIP floatingIp = client.networking().floatingip().get(osFloatingIpId);
      if (floatingIp != null) requireProjectOwnership(osProjectId, floatingIp.getTenantId(), "floating IP");
      return floatingIp;
    } catch (ResponseException error) {
      if (error.getStatus() == 404) return null;
      throw error;
    }
  }

  private void requireProjectFloatingIp(OSClient.OSClientV3 client, String osProjectId, String osFloatingIpId) {
    if (getProjectFloatingIp(client, osProjectId, osFloatingIpId) == null) {
      throw new OpenStackOperationException("Floating IP does not exist in the requested project", null);
    }
  }

  private static void requireDeleteResponse(ActionResponse response) {
    if (response == null || (!response.isSuccess() && response.getCode() != 404)) {
      throw new IllegalStateException("Neutron did not confirm deletion");
    }
  }

  private static boolean allowsInstancePort(SecurityGroupRule rule, String osSecurityGroupId, int port, NetProtocol protocol) {
    return osSecurityGroupId.equals(rule.getSecurityGroupId()) && "ingress".equalsIgnoreCase(rule.getDirection())
        && "IPv4".equalsIgnoreCase(rule.getEtherType()) && protocol.name().equalsIgnoreCase(rule.getProtocol())
        && rule.getRemoteGroupId() == null
        && (rule.getRemoteIpPrefix() == null || "0.0.0.0/0".equals(rule.getRemoteIpPrefix()))
        && (rule.getPortRangeMin() == null || rule.getPortRangeMin() <= port)
        && (rule.getPortRangeMax() == null || rule.getPortRangeMax() >= port);
  }

  public void deleteServer(String region, String osProjectId, String osServerId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      client.compute().servers().delete(osServerId);
    } catch (Exception e) {
      log.error("Failed to delete server: " + osServerId, e);
    }
  }

  public void disassociateFloatIP(String region, String osProjectId, String osFloatingIpId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      client.networking().floatingip().disassociateFromPort(osFloatingIpId);
    } catch (Exception e) {
      log.error("Failed to disassociate floatip: " + osFloatingIpId, e);
    }
  }

  public void deleteFloatIP(String region, String osProjectId, String osFloatingIpId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      client.networking().floatingip().delete(osFloatingIpId);
    } catch (Exception e) {
      log.error("Failed to delete floatip: " + osFloatingIpId, e);
    }
  }

  public void deletePort(String region, String osProjectId, String osPortId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      client.networking().port().delete(osPortId);
    } catch (Exception e) {
      log.error("Failed to delete port: " + osPortId, e);
    }
  }

  public void deleteTrunk(String region, String osProjectId, String osTrunkId) {
    try {
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      client.networking().trunk().delete(osTrunkId);
    } catch (Exception e) {
      log.error("Failed to delete trunk: " + osTrunkId, e);
    }
  }

  /** Resumes the user network by reusing each confirmed resource before creating a missing step. */
  public CompletionStage<NetworkResources> createUserNetwork(NetworkSpec spec, String osProjectId, String name) {
    return CompletableFuture.supplyAsync(() -> {
      String regionId = spec.regionId();
      String osLocalNetworkId = resolveUserLocalNetwork(regionId);

      Network network = uniqueResource(listNetworks(regionId, osProjectId).stream()
          .filter(item -> osProjectId.equals(item.getTenantId()) && name.equals(item.getName())).toList(),
          "user network");
      if (network == null) network = createNetwork(regionId, osProjectId, name);
      if (network == null) throw new IllegalStateException("Neutron returned no network");
      if (!osProjectId.equals(network.getTenantId()) || network.isRouterExternal() || network.isShared()) {
        throw new OpenStackOperationException("User network has mismatched ownership or visibility", null);
      }
      String osNetworkId = networkResourceId(network.getId());

      Subnet subnet = uniqueResource(listSubnets(regionId, osProjectId).stream()
          .filter(item -> osProjectId.equals(item.getTenantId()) && (name + "-subnet").equals(item.getName())).toList(),
          "user subnet");
      if (subnet == null) subnet = createSubnet(regionId, osProjectId, osNetworkId, name + "-subnet",
          spec.ipv4CIDR(), spec.dns(), spec.gateway(), Map.of());
      if (subnet == null) throw new IllegalStateException("Neutron returned no subnet");
      if (!osProjectId.equals(subnet.getTenantId()) || !osNetworkId.equals(subnet.getNetworkId())
          || !spec.ipv4CIDR().equals(subnet.getCidr()) || subnet.getIpVersion() != IPVersionType.V4
          || !subnet.isDHCPEnabled() || !userNetworkDns(spec.dns()).equals(
              subnet.getDnsNames() == null ? Set.of() : new HashSet<>(subnet.getDnsNames()))
          || (spec.gateway() != null && !spec.gateway().equals(subnet.getGateway()))) {
        throw new OpenStackOperationException("User subnet has mismatched ownership or network settings", null);
      }
      String osSubnetId = networkResourceId(subnet.getId());

      Router router = uniqueResource(listRouters(regionId, osProjectId).stream()
          .filter(item -> osProjectId.equals(item.getTenantId()) && (name + "-router").equals(item.getName())).toList(),
          "user router");
      if (router == null) router = createRouter(regionId, osProjectId, name + "-router", osLocalNetworkId);
      if (router == null) throw new IllegalStateException("Neutron returned no router");
      if (!osProjectId.equals(router.getTenantId()) || router.getExternalGatewayInfo() == null
          || !osLocalNetworkId.equals(router.getExternalGatewayInfo().getNetworkId())) {
        throw new OpenStackOperationException("User router has mismatched ownership or LOCAL network gateway", null);
      }
      String osRouterId = networkResourceId(router.getId());

      Port existing = findUserRouterInterface(regionId, osProjectId, osRouterId, osNetworkId, osSubnetId);
      String osRouterPortId;
      if (existing != null) {
        osRouterPortId = networkResourceId(existing.getId());
      } else {
        RouterInterface attached = attachRouterInterface(regionId, osProjectId, osRouterId, osSubnetId);
        if (attached == null || !osSubnetId.equals(attached.getSubnetId()) || !osRouterId.equals(attached.getId())) {
          throw new IllegalStateException("Neutron did not confirm the user router interface");
        }
        osRouterPortId = networkResourceId(attached.getPortId());
      }
      return new NetworkResources(osLocalNetworkId, osNetworkId, osSubnetId, osRouterId, osRouterPortId);
    }, NETWORK_EXECUTOR);
  }

  private Port findUserRouterInterface(String regionId, String osProjectId, String osRouterId,
      String osNetworkId, String osSubnetId) {
    Port port = uniqueResource(listPorts(regionId, osProjectId).stream()
        .filter(item -> osRouterId.equals(item.getDeviceId()) && item.getFixedIps() != null
            && item.getFixedIps().stream().anyMatch(ip -> osSubnetId.equals(ip.getSubnetId())))
        .toList(), "user router interface");
    if (port != null && (!osProjectId.equals(port.getTenantId()) || !osNetworkId.equals(port.getNetworkId())
        || !Set.of("network:router_interface", "network:router_interface_distributed",
            "network:ha_router_replicated_interface").contains(Objects.toString(port.getDeviceOwner(), "")))) {
      throw new OpenStackOperationException("User router interface has mismatched ownership or network", null);
    }
    return port;
  }

  private static Set<String> userNetworkDns(String dns) {
    if (dns == null || dns.isBlank()) return Set.of();
    return new HashSet<>(Arrays.stream(dns.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList());
  }

  private static <T> T uniqueResource(List<? extends T> matches, String resource) {
    if (matches.size() > 1) throw new OpenStackOperationException("Multiple matching " + resource + " resources", null);
    return matches.isEmpty() ? null : matches.getFirst();
  }

  private String resolveUserLocalNetwork(String regionId) {
    List<SysNetworkEntity> localNetworks = sysNetworkRepo.findByRegionId(regionId).stream()
        .filter(network -> network.getSysNetworkType() == SysNetworkType.LOCAL).toList();
    if (localNetworks.size() != 1) {
      throw new OpenStackOperationException("Exactly one preset LOCAL network is required in this region", null);
    }
    SysNetworkEntity local = localNetworks.getFirst();
    if (!local.isRouterExternally() || !local.isShared()
        || local.getOsNetId() == null || local.getOsNetId().isBlank()) {
      throw new OpenStackOperationException("Preset LOCAL network must be shared and external with an OpenStack ID", null);
    }
    Network network = getNetwork(regionId, local.getOsProjectId(), local.getOsNetId());
    if (network == null || !network.isRouterExternal() || !network.isShared()) {
      throw new OpenStackOperationException("Preset LOCAL network is not a shared external Neutron network", null);
    }
    return networkResourceId(network.getId());
  }

  private static String networkResourceId(String id) {
    if (id == null || id.isBlank()) throw new IllegalStateException("Missing network resource ID");
    return id;
  }

  public Router createRouter(String regionId, String osProjectId, String name, String osExtNetId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
    return client
        .networking()
        .router()
        .create(Builders.router().name(name).tenantId(osProjectId)
            .adminStateUp(true).externalGateway(osExtNetId).build());
  }

  public Network createNetwork(String regionId, String osProjectId, String name) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
    return client
        .networking()
        .network()
        .create(Builders.network().name(name).tenantId(osProjectId).adminStateUp(true)
            .isShared(false).isRouterExternal(false).build());
  }

  public Network createNetwork(
      String regionId,
      String osProjectId,
      String name,
      NetworkType networkType,
      boolean isRouterExternal,
      boolean isShared,
      String physicalNetwork,
      String segmentId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
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
      String osProjectId,
      String osNetworkId,
      String name,
      String ipv4CIDR,
      String dns,
      String gateway,
      Map<String, String> hostRoute) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
    SubnetBuilder subnetBuilder =
        Builders.subnet()
            .name(name)
            .tenantId(osProjectId)
            .ipVersion(IPVersionType.V4)
            .cidr(ipv4CIDR)
            .networkId(osNetworkId)
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
      String regionId, String osProjectId, String osRouterId, String osSubnetId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
    return client
        .networking()
        .router()
        .attachInterface(osRouterId, AttachInterfaceType.SUBNET, osSubnetId);
  }

  public Network getNetwork(String regionId, String osProjectId, String osNetworkId) {
    Network network = getOsAdminClient(regionId, osProjectId).networking().network().get(osNetworkId);
    if (network != null) requireProjectOwnership(osProjectId, network.getTenantId(), "network");
    return network;
  }

  public Subnet getSubnet(String regionId, String osProjectId, String osSubnetId) {
    Subnet subnet = getOsAdminClient(regionId, osProjectId).networking().subnet().get(osSubnetId);
    if (subnet != null) requireProjectOwnership(osProjectId, subnet.getTenantId(), "subnet");
    return subnet;
  }

  public List<? extends Network> listNetworks(String regionId, String osProjectId) {
    return getOsAdminClient(regionId, osProjectId).networking().network().list(Map.of("project_id", osProjectId));
  }

  public List<? extends Subnet> listSubnets(String regionId, String osProjectId) {
    return getOsAdminClient(regionId, osProjectId).networking().subnet().list(Map.of("project_id", osProjectId));
  }

  public List<? extends Router> listRouters(String regionId, String osProjectId) {
    return getOsAdminClient(regionId, osProjectId).networking().router().list(Map.of("project_id", osProjectId));
  }

  public List<? extends Port> listPorts(String regionId, String osProjectId) {
    return getOsAdminClient(regionId, osProjectId).networking().port().list(Map.of("project_id", List.of(osProjectId)));
  }

  /** Creates a port using the caller's network, security group and prepared QoS policy. */
  private Port createPort(OSClient.OSClientV3 client, Port request) {
    Port port = client.networking().port().create(request);
    if (port == null) throw new IllegalStateException("Neutron returned no port");
    return port;
  }

  /** Creates a dedicated QoS policy and its bandwidth rules without creating a port. */
  private PreparedPortQos createPortQos(OSClient.OSClientV3 client, String osProjectId, String portName,
      PortBandwidthLimit bandwidthLimit) {
    Objects.requireNonNull(bandwidthLimit, "bandwidthLimit");
    requireServerText(osProjectId, "osProjectId");
    requireServerText(portName, "portName");
    try {
      String marker = portQosMarker(portName);
      NeutronNetQosPolicy policy = (NeutronNetQosPolicy) client.networking().netQosPolicy().create(
          NeutronNetQosPolicy.builder().name(marker).description(marker)
              .tenantId(osProjectId).shared(false).isDefault(false).build());
      if (policy == null) throw new IllegalStateException("Neutron returned no port QoS policy");
      requireServerText(policy.getId(), "osPolicyId");
      return createPortBandwidthRules(client, policy.getId(), bandwidthLimit);
    } catch (ResponseException error) {
      int status = error.getStatus();
      if (status >= 400 && status < 500 && status != 408 && status != 429) {
        throw new OpenStackOperationException("Neutron rejected instance port QoS preparation", error);
      }
      throw error;
    }
  }

  /** Creates each rule once and records its ID directly from the create response. */
  private PreparedPortQos createPortBandwidthRules(OSClient.OSClientV3 client, String osPolicyId,
      PortBandwidthLimit bandwidthLimit) {
    if (!bandwidthLimit.limited()) return new PreparedPortQos(osPolicyId, null, null);
    var service = client.networking().netQosPolicyBandwidthLimitRule();
    Map<NetQosPolicyBandwidthLimitRule.Direction, String> osRuleIds =
        new EnumMap<>(NetQosPolicyBandwidthLimitRule.Direction.class);
    for (NetQosPolicyBandwidthLimitRule.Direction direction : NetQosPolicyBandwidthLimitRule.Direction.values()) {
      NetQosPolicyBandwidthLimitRule rule = service.create(osPolicyId, NeutronNetQosPolicyBandwidthLimitRule.builder()
          .direction(direction).maxKbps(bandwidthLimit.maxKbps())
          .maxBurstKbps(bandwidthLimit.maxBurstKbits()).build());
      if (rule == null) throw new IllegalStateException("Neutron returned no bandwidth limit rule");
      requireServerText(rule.getId(), "osRuleId");
      if (!Objects.equals(rule.getMaxKbps(), bandwidthLimit.maxKbps())
          || Objects.requireNonNullElse(rule.getMaxBurstKbps(), 0) != bandwidthLimit.maxBurstKbits()
          || Objects.requireNonNullElse(rule.getDirection(), NetQosPolicyBandwidthLimitRule.Direction.egress) != direction) {
        throw new OpenStackOperationException("Port bandwidth limit does not match the create command", null);
      }
      osRuleIds.put(direction, rule.getId());
    }
    return new PreparedPortQos(osPolicyId, osRuleIds.get(NetQosPolicyBandwidthLimitRule.Direction.ingress),
        osRuleIds.get(NetQosPolicyBandwidthLimitRule.Direction.egress));
  }

  /** Queries the port's existing QoS resources without creating or repairing them. */
  private PreparedPortQos findPortQos(OSClient.OSClientV3 client, String osProjectId, String portName,
      String osPolicyId, PortBandwidthLimit bandwidthLimit) {
    Objects.requireNonNull(bandwidthLimit, "bandwidthLimit");
    requireServerText(osPolicyId, "osPolicyId");
    NetQosPolicy policy = client.networking().netQosPolicy().get(osPolicyId);
    if (!isPortQosPolicy(policy, osProjectId, portName)) {
      throw new OpenStackOperationException("Instance port has a mismatched QoS policy", null);
    }
    return verifyPortBandwidthRules(client, osPolicyId, bandwidthLimit);
  }

  /** Verifies existing bandwidth rules without filling in missing rules during queries. */
  private PreparedPortQos verifyPortBandwidthRules(OSClient.OSClientV3 client, String osPolicyId,
      PortBandwidthLimit bandwidthLimit) {
    List<? extends NetQosPolicyBandwidthLimitRule> rules = client.networking()
        .netQosPolicyBandwidthLimitRule().list(osPolicyId);
    Map<NetQosPolicyBandwidthLimitRule.Direction, String> ruleIds =
        portBandwidthRuleIds(rules, bandwidthLimit, false);
    return new PreparedPortQos(osPolicyId, ruleIds.get(NetQosPolicyBandwidthLimitRule.Direction.ingress),
        ruleIds.get(NetQosPolicyBandwidthLimitRule.Direction.egress));
  }

  private Map<NetQosPolicyBandwidthLimitRule.Direction, String> portBandwidthRuleIds(
      List<? extends NetQosPolicyBandwidthLimitRule> rules, PortBandwidthLimit bandwidthLimit,
      boolean allowMissing) {
    Map<NetQosPolicyBandwidthLimitRule.Direction, String> ruleIds =
        new EnumMap<>(NetQosPolicyBandwidthLimitRule.Direction.class);
    if (!bandwidthLimit.limited()) {
      if (!rules.isEmpty()) {
        throw new OpenStackOperationException("Unlimited port QoS policy contains bandwidth limit rules", null);
      }
      return ruleIds;
    }
    for (NetQosPolicyBandwidthLimitRule.Direction direction : NetQosPolicyBandwidthLimitRule.Direction.values()) {
      List<? extends NetQosPolicyBandwidthLimitRule> matches = rules.stream()
          .filter(rule -> Objects.requireNonNullElse(rule.getDirection(),
              NetQosPolicyBandwidthLimitRule.Direction.egress) == direction).toList();
      if (matches.size() > 1) {
        throw new OpenStackOperationException("Duplicate port bandwidth limit direction", null);
      }
      if (matches.isEmpty()) {
        if (allowMissing) continue;
        throw new OpenStackOperationException("Port QoS policy is missing a bandwidth limit rule", null);
      }
      NetQosPolicyBandwidthLimitRule rule = matches.getFirst();
      requireServerText(rule.getId(), "bandwidthLimitRuleId");
      if (!Objects.equals(rule.getMaxKbps(), bandwidthLimit.maxKbps())
          || Objects.requireNonNullElse(rule.getMaxBurstKbps(), 0) != bandwidthLimit.maxBurstKbits()
          || Objects.requireNonNullElse(rule.getDirection(), NetQosPolicyBandwidthLimitRule.Direction.egress) != direction) {
        throw new OpenStackOperationException("Port bandwidth limit does not match the create command", null);
      }
      ruleIds.put(direction, rule.getId());
    }
    return ruleIds;
  }

  /** Reuses orphan policies and completes only missing directions after an interrupted preparation. */
  private PreparedPortQos recoverPortQos(OSClient.OSClientV3 client, String osProjectId, String portName,
      String boundPolicyId, PortBandwidthLimit bandwidthLimit) {
    Objects.requireNonNull(bandwidthLimit, "bandwidthLimit");
    NeutronNetQosPolicy policy = uniqueResource(findPortQosPolicies(client, osProjectId, portName), "instance port QoS policy");
    if (boundPolicyId != null) {
      requireServerText(boundPolicyId, "osPolicyId");
      if (policy != null && !boundPolicyId.equals(policy.getId())) {
        throw new OpenStackOperationException("Instance port is bound to a different QoS policy", null);
      }
      if (policy == null) policy = (NeutronNetQosPolicy) client.networking().netQosPolicy().get(boundPolicyId);
      if (!isPortQosPolicy(policy, osProjectId, portName)) {
        throw new OpenStackOperationException("Instance port has a mismatched QoS policy", null);
      }
    } else if (policy == null) {
      String marker = portQosMarker(portName);
      try {
        policy = (NeutronNetQosPolicy) client.networking().netQosPolicy().create(NeutronNetQosPolicy.builder()
            .name(marker).description(marker).tenantId(osProjectId).shared(false).isDefault(false).build());
      } catch (ResponseException error) {
        if (error.getStatus() != 409) throw error;
        policy = uniqueResource(findPortQosPolicies(client, osProjectId, portName), "instance port QoS policy");
        if (policy == null) throw error;
      }
    }
    if (!isPortQosPolicy(policy, osProjectId, portName)) {
      throw new OpenStackOperationException("Missing or mismatched instance port QoS policy", null);
    }
    requireServerText(policy.getId(), "osPolicyId");
    String osPolicyId = policy.getId();
    var service = client.networking().netQosPolicyBandwidthLimitRule();
    Map<NetQosPolicyBandwidthLimitRule.Direction, String> existing =
        portBandwidthRuleIds(service.list(osPolicyId), bandwidthLimit, true);
    if (bandwidthLimit.limited()) {
      for (NetQosPolicyBandwidthLimitRule.Direction direction : NetQosPolicyBandwidthLimitRule.Direction.values()) {
        if (existing.containsKey(direction)) continue;
        try {
          NetQosPolicyBandwidthLimitRule created = service.create(osPolicyId,
              NeutronNetQosPolicyBandwidthLimitRule.builder().direction(direction)
                  .maxKbps(bandwidthLimit.maxKbps()).maxBurstKbps(bandwidthLimit.maxBurstKbits()).build());
          if (created == null) throw new OpenStackOperationException("Neutron returned no bandwidth limit rule", null);
          Map<NetQosPolicyBandwidthLimitRule.Direction, String> createdIds =
              portBandwidthRuleIds(List.of(created), bandwidthLimit, true);
          if (!createdIds.containsKey(direction)) {
            throw new OpenStackOperationException("Port bandwidth limit direction does not match the request", null);
          }
          existing.putAll(createdIds);
        } catch (ResponseException error) {
          if (error.getStatus() != 409) throw error;
          existing = portBandwidthRuleIds(service.list(osPolicyId), bandwidthLimit, true);
          if (!existing.containsKey(direction)) throw error;
        }
      }
    }
    return verifyPortBandwidthRules(client, osPolicyId, bandwidthLimit);
  }

  private List<NeutronNetQosPolicy> findPortQosPolicies(OSClient.OSClientV3 client,
      String osProjectId, String portName) {
    return client.networking().netQosPolicy().list(Map.of("project_id", osProjectId)).stream()
        .filter(policy -> isPortQosPolicy(policy, osProjectId, portName))
        .map(policy -> (NeutronNetQosPolicy) policy).toList();
  }

  private static boolean isPortQosPolicy(NetQosPolicy policy, String osProjectId, String portName) {
    return policy != null && osProjectId.equals(policy.getTenantId())
        && portQosMarker(portName).equals(policy.getDescription());
  }

  private static String portQosMarker(String portName) {
    return portName + "-qos";
  }

  private enum InstanceNetworkStep { PARENT_PORT, SUB_PORT, TRUNK }

  /** Prepares QoS first, then creates the parent port using that policy. */
  public CompletionStage<PreparedNetwork> prepareInstanceParentPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.PARENT_PORT);
        String osNetworkId = requirement.osParentNetworkId();
        String osSubnetId = requirement.osParentSubnetId();
        PreparedPortQos qos = createPortQos(client, target.osProjectId(), marker,
            requirement.parentPortBandwidthLimit());
        var request = Builders.port().name(marker).tenantId(target.osProjectId())
            .networkId(osNetworkId).fixedIp(null, osSubnetId).adminState(true)
            .portSecurityEnabled(true).securityGroup(osSecurityGroupId).qosPolicyId(qos.osPolicyId()).build();
        Port port = createPort(client, request);
        if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
            || !Set.of(osSecurityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
          throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
        }
        if (!osNetworkId.equals(port.getNetworkId()) || port.getFixedIps() == null) {
          throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
        }
        requireServerText(port.getId(), "osPortId");
        requireServerText(port.getMacAddress(), "portMacAddress");
        if (!qos.osPolicyId().equals(port.getQosPolicyId())) {
          throw new OpenStackOperationException("Instance port is not bound to its prepared QoS policy", null);
        }
        String fixedIp = port.getFixedIps().stream().filter(ip -> osSubnetId.equals(ip.getSubnetId()))
            .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
            .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
        return PreparedNetwork.parentPort(port.getId(), port.getMacAddress(), fixedIp, qos);
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance parent_port preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Queries and validates the parent port and its QoS resources without creating anything. */
  public CompletionStage<PreparedNetwork> findInstanceParentPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.PARENT_PORT);
        String osNetworkId = requirement.osParentNetworkId();
        String osSubnetId = requirement.osParentSubnetId();
        List<? extends Port> matches = client.networking().port().list(
            Map.of("project_id", List.of(target.osProjectId()), "name", List.of(marker))).stream()
            .filter(port -> marker.equals(port.getName()) && target.osProjectId().equals(port.getTenantId()))
            .toList();
        if (matches.size() > 1) throw new OpenStackOperationException("Duplicate instance port marker", null);
        Port port = matches.isEmpty() ? null : matches.getFirst();
        if (port == null) return null;
        if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
            || !Set.of(osSecurityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
          throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
        }
        if (!osNetworkId.equals(port.getNetworkId()) || port.getFixedIps() == null) {
          throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
        }
        requireServerText(port.getId(), "osPortId");
        requireServerText(port.getMacAddress(), "portMacAddress");
        PreparedPortQos qos = findPortQos(client, target.osProjectId(), marker,
            port.getQosPolicyId(), requirement.parentPortBandwidthLimit());
        String fixedIp = port.getFixedIps().stream().filter(ip -> osSubnetId.equals(ip.getSubnetId()))
            .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
            .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
        return PreparedNetwork.parentPort(port.getId(), port.getMacAddress(), fixedIp, qos);
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance parent_port preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Prepares QoS, creates the subport using that policy, then allocates a dedicated floating IP. */
  public CompletionStage<PreparedNetwork> prepareInstanceSubPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.SUB_PORT);
        String osNetworkId = requirement.osSubNetworkId();
        String osSubnetId = requirement.osSubSubnetId();
        PreparedPortQos qos = createPortQos(client, target.osProjectId(), marker,
            requirement.subPortBandwidthLimit());
        var request = Builders.port().name(marker).tenantId(target.osProjectId())
            .networkId(osNetworkId).fixedIp(null, osSubnetId).adminState(true)
            .portSecurityEnabled(true).securityGroup(osSecurityGroupId)
            .macAddress(prepared.parentMacAddress()).qosPolicyId(qos.osPolicyId()).build();
        Port port = createPort(client, request);
        if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
            || !Set.of(osSecurityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
          throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
        }
        if (!osNetworkId.equals(port.getNetworkId()) || port.getFixedIps() == null
            || !prepared.parentMacAddress().equalsIgnoreCase(port.getMacAddress())) {
          throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
        }
        requireServerText(port.getId(), "osPortId");
        requireServerText(port.getMacAddress(), "portMacAddress");
        if (!qos.osPolicyId().equals(port.getQosPolicyId())) {
          throw new OpenStackOperationException("Instance port is not bound to its prepared QoS policy", null);
        }
        String fixedIp = port.getFixedIps().stream().filter(ip -> osSubnetId.equals(ip.getSubnetId()))
            .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
            .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
        NetFloatingIP floatingIp = createInstanceSubPortFloatingIp(client, target.osProjectId(), operationId,
            requirement.osSubFloatingNetworkId(), port.getId(), fixedIp);
        return prepared.withSubPort(port.getId(), fixedIp, floatingIp.getId(),
            floatingIp.getFloatingIpAddress(), qos);
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance sub_port preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Queries the subport, its QoS resources and floating IP without creating anything. */
  public CompletionStage<PreparedNetwork> findInstanceSubPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.SUB_PORT);
        String osNetworkId = requirement.osSubNetworkId();
        String osSubnetId = requirement.osSubSubnetId();
        List<? extends Port> matches = client.networking().port().list(
            Map.of("project_id", List.of(target.osProjectId()), "name", List.of(marker))).stream()
            .filter(port -> marker.equals(port.getName()) && target.osProjectId().equals(port.getTenantId()))
            .toList();
        if (matches.size() > 1) throw new OpenStackOperationException("Duplicate instance port marker", null);
        Port port = matches.isEmpty() ? null : matches.getFirst();
        if (port == null) return null;
        if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
            || !Set.of(osSecurityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
          throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
        }
        if (!osNetworkId.equals(port.getNetworkId()) || port.getFixedIps() == null
            || !prepared.parentMacAddress().equalsIgnoreCase(port.getMacAddress())) {
          throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
        }
        requireServerText(port.getId(), "osPortId");
        requireServerText(port.getMacAddress(), "portMacAddress");
        PreparedPortQos qos = findPortQos(client, target.osProjectId(), marker,
            port.getQosPolicyId(), requirement.subPortBandwidthLimit());
        String fixedIp = port.getFixedIps().stream().filter(ip -> osSubnetId.equals(ip.getSubnetId()))
            .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
            .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
        NetFloatingIP floatingIp = findInstanceSubPortFloatingIp(client, target.osProjectId(), operationId,
            requirement.osSubFloatingNetworkId(), port.getId(), fixedIp);
        if (floatingIp == null) return null;
        return prepared.withSubPort(port.getId(), fixedIp, floatingIp.getId(),
            floatingIp.getFloatingIpAddress(), qos);
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance sub_port preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Creates the trunk using the persisted parent and subport IDs. */
  public CompletionStage<PreparedNetwork> prepareInstanceTrunk(ServerTarget target,
      String operationId, NetworkRequirement requirement, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    requireServerText(prepared.osSubPortId(), "osSubPortId");
    requireServerText(prepared.subFixedIp(), "subFixedIp");
    requireServerText(prepared.osSubFloatingIpId(), "osSubFloatingIpId");
    requireServerText(prepared.subFloatingIpAddress(), "subFloatingIpAddress");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.TRUNK);
        NeutronTrunkSubport subport = NeutronTrunkSubport.fromTrunkSubport(NeutronTrunkSubport.builder()
            .portId(prepared.osSubPortId()).segmentationType("vlan")
            .segmentationId(requirement.segmentationId()).build());
        Trunk trunk = client.networking().trunk().create(Builders.neutron().trunk()
            .name(marker).description(marker).tenantId(target.osProjectId()).adminState(true)
            .parentPort(prepared.osParentPortId()).trunkSubports(List.of(subport)).build());
        if (trunk == null) throw new IllegalStateException("Neutron returned no trunk");
        if (!prepared.osParentPortId().equals(trunk.getParentPort())
            || trunk.getTrunkSubports() == null || trunk.getTrunkSubports().size() != 1
            || trunk.getTrunkSubports().stream().noneMatch(sub -> prepared.osSubPortId().equals(sub.getPortId())
                && "vlan".equals(sub.getSegmentationType())
                && requirement.segmentationId() == sub.getSegmentationId())) {
          throw new OpenStackOperationException("Instance trunk has mismatched ports or VLAN", null);
        }
        requireServerText(trunk.getId(), "osTrunkId");
        return prepared.withTrunk(trunk.getId());
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance trunk preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Queries and validates the trunk without creating resources. */
  public CompletionStage<PreparedNetwork> findInstanceTrunk(ServerTarget target,
      String operationId, NetworkRequirement requirement, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    requireServerText(prepared.osSubPortId(), "osSubPortId");
    requireServerText(prepared.subFixedIp(), "subFixedIp");
    requireServerText(prepared.osSubFloatingIpId(), "osSubFloatingIpId");
    requireServerText(prepared.subFloatingIpAddress(), "subFloatingIpAddress");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.TRUNK);
        List<? extends Trunk> matches = client.networking().trunk().list(Map.of("project_id", target.osProjectId())).stream()
            .filter(trunk -> marker.equals(trunk.getDescription())
                && target.osProjectId().equals(trunk.getTenantId())).toList();
        if (matches.size() > 1) throw new OpenStackOperationException("Duplicate instance trunk marker", null);
        Trunk trunk = matches.isEmpty() ? null : matches.getFirst();
        if (trunk == null) return null;
        if (!prepared.osParentPortId().equals(trunk.getParentPort())
            || trunk.getTrunkSubports() == null || trunk.getTrunkSubports().size() != 1
            || trunk.getTrunkSubports().stream().noneMatch(sub -> prepared.osSubPortId().equals(sub.getPortId())
                && "vlan".equals(sub.getSegmentationType())
                && requirement.segmentationId() == sub.getSegmentationId())) {
          throw new OpenStackOperationException("Instance trunk has mismatched ports or VLAN", null);
        }
        requireServerText(trunk.getId(), "osTrunkId");
        return prepared.withTrunk(trunk.getId());
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance trunk preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  /** Resumes the parent stage, reusing its policy/rules and creating only the missing port. */
  public CompletionStage<PreparedNetwork> recoverInstanceParentPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.PARENT_PORT);
        Port port = findInstancePort(client, target.osProjectId(), marker);
        if (port != null) validateInstancePort(port, target.osProjectId(), marker,
            requirement.osParentNetworkId(), requirement.osParentSubnetId(), osSecurityGroupId, null);
        PreparedPortQos qos = recoverPortQos(client, target.osProjectId(), marker,
            port == null ? null : port.getQosPolicyId(), requirement.parentPortBandwidthLimit());
        if (port == null) {
          Port request = Builders.port().name(marker).tenantId(target.osProjectId())
              .networkId(requirement.osParentNetworkId()).fixedIp(null, requirement.osParentSubnetId())
              .adminState(true).portSecurityEnabled(true).securityGroup(osSecurityGroupId)
              .qosPolicyId(qos.osPolicyId()).build();
          port = recoverPortCreation(client, target.osProjectId(), marker, request);
        }
        String fixedIp = validateInstancePort(port, target.osProjectId(), marker,
            requirement.osParentNetworkId(), requirement.osParentSubnetId(), osSecurityGroupId, null);
        if (!qos.osPolicyId().equals(port.getQosPolicyId())) {
          throw new OpenStackOperationException("Instance port is not bound to its recovered QoS policy", null);
        }
        return PreparedNetwork.parentPort(port.getId(), port.getMacAddress(), fixedIp, qos);
      } catch (ResponseException error) {
        throw networkRecoveryFailure("parent port", error);
      }
    }, NETWORK_EXECUTOR);
  }

  /** Resumes QoS, the subport and its floating IP separately without replacing existing resources. */
  public CompletionStage<PreparedNetwork> recoverInstanceSubPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String osSecurityGroupId, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(osSecurityGroupId, "osSecurityGroupId");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.SUB_PORT);
        Port port = findInstancePort(client, target.osProjectId(), marker);
        if (port != null) validateInstancePort(port, target.osProjectId(), marker,
            requirement.osSubNetworkId(), requirement.osSubSubnetId(), osSecurityGroupId, prepared.parentMacAddress());
        PreparedPortQos qos = recoverPortQos(client, target.osProjectId(), marker,
            port == null ? null : port.getQosPolicyId(), requirement.subPortBandwidthLimit());
        if (port == null) {
          Port request = Builders.port().name(marker).tenantId(target.osProjectId())
              .networkId(requirement.osSubNetworkId()).fixedIp(null, requirement.osSubSubnetId())
              .adminState(true).portSecurityEnabled(true).securityGroup(osSecurityGroupId)
              .macAddress(prepared.parentMacAddress()).qosPolicyId(qos.osPolicyId()).build();
          port = recoverPortCreation(client, target.osProjectId(), marker, request);
        }
        String fixedIp = validateInstancePort(port, target.osProjectId(), marker,
            requirement.osSubNetworkId(), requirement.osSubSubnetId(), osSecurityGroupId, prepared.parentMacAddress());
        if (!qos.osPolicyId().equals(port.getQosPolicyId())) {
          throw new OpenStackOperationException("Instance port is not bound to its recovered QoS policy", null);
        }
        NetFloatingIP floatingIp = findInstanceSubPortFloatingIp(client, target.osProjectId(), operationId,
            requirement.osSubFloatingNetworkId(), port.getId(), fixedIp);
        if (floatingIp == null) {
          try {
            floatingIp = createInstanceSubPortFloatingIp(client, target.osProjectId(), operationId,
                requirement.osSubFloatingNetworkId(), port.getId(), fixedIp);
          } catch (ResponseException error) {
            if (error.getStatus() != 409) throw error;
            floatingIp = findInstanceSubPortFloatingIp(client, target.osProjectId(), operationId,
                requirement.osSubFloatingNetworkId(), port.getId(), fixedIp);
            if (floatingIp == null) throw error;
          }
        }
        return prepared.withSubPort(port.getId(), fixedIp, floatingIp.getId(), floatingIp.getFloatingIpAddress(), qos);
      } catch (ResponseException error) {
        throw networkRecoveryFailure("subport", error);
      }
    }, NETWORK_EXECUTOR);
  }

  /** Reuses a matching trunk, completing a missing attachment or creating the missing trunk. */
  public CompletionStage<PreparedNetwork> recoverInstanceTrunk(ServerTarget target,
      String operationId, NetworkRequirement requirement, PreparedNetwork prepared) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    requireServerText(prepared.osParentPortId(), "osParentPortId");
    requireServerText(prepared.parentMacAddress(), "parentMacAddress");
    requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    requireServerText(prepared.osSubPortId(), "osSubPortId");
    requireServerText(prepared.subFixedIp(), "subFixedIp");
    requireServerText(prepared.osSubFloatingIpId(), "osSubFloatingIpId");
    requireServerText(prepared.subFloatingIpAddress(), "subFloatingIpAddress");
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
        String marker = instanceNetworkMarker(operationId, InstanceNetworkStep.TRUNK);
        var service = client.networking().trunk();
        Trunk trunk = findInstanceTrunk(client, target.osProjectId(), marker);
        NeutronTrunkSubport subport = NeutronTrunkSubport.fromTrunkSubport(NeutronTrunkSubport.builder()
            .portId(prepared.osSubPortId()).segmentationType("vlan")
            .segmentationId(requirement.segmentationId()).build());
        if (trunk == null) {
          try {
            trunk = service.create(Builders.neutron().trunk().name(marker).description(marker)
                .tenantId(target.osProjectId()).adminState(true).parentPort(prepared.osParentPortId())
                .trunkSubports(List.of(subport)).build());
          } catch (ResponseException error) {
            if (error.getStatus() != 409) throw error;
            trunk = findInstanceTrunk(client, target.osProjectId(), marker);
            if (trunk == null) throw error;
          }
        }
        validateInstanceTrunk(trunk, target.osProjectId(), marker, requirement, prepared, true);
        if (trunk.getTrunkSubports().isEmpty()) {
          String osTrunkId = trunk.getId();
          try {
            service.addTrunkSubport(osTrunkId, subport);
          } catch (ResponseException error) {
            if (error.getStatus() != 409) throw error;
            Trunk observed = service.get(osTrunkId);
            validateInstanceTrunk(observed, target.osProjectId(), marker, requirement, prepared, false);
          }
          trunk = service.get(osTrunkId);
        }
        validateInstanceTrunk(trunk, target.osProjectId(), marker, requirement, prepared, false);
        return prepared.withTrunk(trunk.getId());
      } catch (ResponseException error) {
        throw networkRecoveryFailure("trunk", error);
      }
    }, NETWORK_EXECUTOR);
  }

  private Port findInstancePort(OSClient.OSClientV3 client, String osProjectId, String marker) {
    return uniqueResource(client.networking().port().list(
        Map.of("project_id", List.of(osProjectId), "name", List.of(marker))).stream()
        .filter(port -> marker.equals(port.getName()) && osProjectId.equals(port.getTenantId())).toList(),
        "instance port");
  }

  private Port recoverPortCreation(OSClient.OSClientV3 client, String osProjectId, String marker, Port request) {
    try {
      return createPort(client, request);
    } catch (ResponseException error) {
      if (error.getStatus() != 409) throw error;
      Port port = findInstancePort(client, osProjectId, marker);
      if (port == null) throw error;
      return port;
    }
  }

  private String validateInstancePort(Port port, String osProjectId, String marker, String osNetworkId,
      String osSubnetId, String osSecurityGroupId, String expectedMacAddress) {
    if (port == null || !osProjectId.equals(port.getTenantId()) || !marker.equals(port.getName())) {
      throw new OpenStackOperationException("Missing or mismatched instance port ownership", null);
    }
    if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
        || !Set.of(osSecurityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
      throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
    }
    if (!osNetworkId.equals(port.getNetworkId()) || port.getFixedIps() == null
        || expectedMacAddress != null && !expectedMacAddress.equalsIgnoreCase(port.getMacAddress())) {
      throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
    }
    requireServerText(port.getId(), "osPortId");
    requireServerText(port.getMacAddress(), "portMacAddress");
    requireServerText(port.getQosPolicyId(), "osPolicyId");
    return port.getFixedIps().stream().filter(ip -> osSubnetId.equals(ip.getSubnetId()))
        .map(IP::getIpAddress).filter(ip -> ip != null && !ip.isBlank()).findFirst()
        .orElseThrow(() -> new OpenStackOperationException("Instance port has no IP in the requested subnet", null));
  }

  private Trunk findInstanceTrunk(OSClient.OSClientV3 client, String osProjectId, String marker) {
    return uniqueResource(client.networking().trunk().list(Map.of("project_id", osProjectId)).stream()
        .filter(trunk -> marker.equals(trunk.getDescription()) && osProjectId.equals(trunk.getTenantId())).toList(),
        "instance trunk");
  }

  private void validateInstanceTrunk(Trunk trunk, String osProjectId, String marker,
      NetworkRequirement requirement, PreparedNetwork prepared, boolean allowMissingSubport) {
    if (trunk == null || !osProjectId.equals(trunk.getTenantId()) || !marker.equals(trunk.getDescription())) {
      throw new OpenStackOperationException("Missing or mismatched instance trunk ownership", null);
    }
    requireServerText(trunk.getId(), "osTrunkId");
    if (!prepared.osParentPortId().equals(trunk.getParentPort()) || trunk.getTrunkSubports() == null) {
      throw new OpenStackOperationException("Instance trunk has mismatched ports or VLAN", null);
    }
    if (allowMissingSubport && trunk.getTrunkSubports().isEmpty()) return;
    if (trunk.getTrunkSubports().size() != 1 || trunk.getTrunkSubports().stream().noneMatch(sub ->
        prepared.osSubPortId().equals(sub.getPortId()) && "vlan".equals(sub.getSegmentationType())
            && requirement.segmentationId() == sub.getSegmentationId())) {
      throw new OpenStackOperationException("Instance trunk has mismatched ports or VLAN", null);
    }
  }

  private RuntimeException networkRecoveryFailure(String resource, ResponseException error) {
    int status = error.getStatus();
    return status >= 400 && status < 500 && status != 408 && status != 429
        ? new OpenStackOperationException("Neutron rejected instance " + resource + " recovery", error) : error;
  }

  private NetFloatingIP createInstanceSubPortFloatingIp(OSClient.OSClientV3 client, String osProjectId,
      String operationId, String osFloatingNetworkId, String osPortId, String fixedIp) {
    String marker = instanceSubPortFloatingIpMarker(operationId);
    NetFloatingIP floatingIp = client.networking().floatingip().create(Builders.netFloatingIP()
        .floatingNetworkId(osFloatingNetworkId).portId(osPortId).fixedIpAddress(fixedIp).description(marker).build());
    if (floatingIp == null) throw new IllegalStateException("Neutron returned no subport floating IP");
    if (!osProjectId.equals(floatingIp.getTenantId()) || !marker.equals(floatingIp.getDescription())
        || !osFloatingNetworkId.equals(floatingIp.getFloatingNetworkId()) || !osPortId.equals(floatingIp.getPortId())
        || !fixedIp.equals(floatingIp.getFixedIpAddress())) {
      throw new OpenStackOperationException("Instance subport floating IP has mismatched ownership, network or binding", null);
    }
    requireServerText(floatingIp.getId(), "osSubFloatingIpId");
    requireServerText(floatingIp.getFloatingIpAddress(), "subFloatingIpAddress");
    return floatingIp;
  }

  private NetFloatingIP findInstanceSubPortFloatingIp(OSClient.OSClientV3 client, String osProjectId,
      String operationId, String osFloatingNetworkId, String osPortId, String fixedIp) {
    String marker = instanceSubPortFloatingIpMarker(operationId);
    NetFloatingIP floatingIp = uniqueResource(findInstanceSubPortFloatingIps(client, osProjectId, operationId),
        "instance subport floating IP");
    // Keep this lookup read-only; recovery separately decides whether the missing address must be created.
    if (floatingIp == null) return null;
    if (!osProjectId.equals(floatingIp.getTenantId()) || !marker.equals(floatingIp.getDescription())
        || !osFloatingNetworkId.equals(floatingIp.getFloatingNetworkId()) || !osPortId.equals(floatingIp.getPortId())
        || !fixedIp.equals(floatingIp.getFixedIpAddress())) {
      throw new OpenStackOperationException("Instance subport floating IP has mismatched ownership, network or binding", null);
    }
    requireServerText(floatingIp.getId(), "osSubFloatingIpId");
    requireServerText(floatingIp.getFloatingIpAddress(), "subFloatingIpAddress");
    return floatingIp;
  }

  private List<? extends NetFloatingIP> findInstanceSubPortFloatingIps(OSClient.OSClientV3 client,
      String osProjectId, String operationId) {
    String marker = instanceSubPortFloatingIpMarker(operationId);
    return client.networking().floatingip().list(Map.of("project_id", osProjectId)).stream()
        .filter(ip -> osProjectId.equals(ip.getTenantId()) && marker.equals(ip.getDescription())).toList();
  }

  private static String instanceSubPortFloatingIpMarker(String operationId) {
    return instanceNetworkMarker(operationId, InstanceNetworkStep.SUB_PORT) + "-fip";
  }

  /** Discover by operation marker as IDs may have been lost with a create response. */
  public CompletionStage<Void> deleteInstanceNetwork(ServerTarget target, String operationId) {
    return CompletableFuture.runAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.osProjectId());
      // Release owned addresses before deleting ports, including addresses whose create response was lost.
      for (NetFloatingIP floatingIp : findInstanceSubPortFloatingIps(client, target.osProjectId(), operationId)) {
        requireDeleteResponse(client.networking().floatingip().delete(floatingIp.getId()));
      }
      String trunkMarker = instanceNetworkMarker(operationId, InstanceNetworkStep.TRUNK);
      for (Trunk trunk : client.networking().trunk().list(Map.of("project_id", target.osProjectId()))) {
        if (!trunkMarker.equals(trunk.getDescription()) || !target.osProjectId().equals(trunk.getTenantId())) continue;
        if (trunk.getTrunkSubports() != null) {
          for (TrunkSubport sub : trunk.getTrunkSubports()) {
            client.networking().trunk().removeTrunkSubport(trunk.getId(), sub.getPortId());
          }
        }
        requireDeleteResponse(client.networking().trunk().delete(trunk.getId()));
      }
      for (InstanceNetworkStep step : List.of(InstanceNetworkStep.SUB_PORT, InstanceNetworkStep.PARENT_PORT)) {
        String marker = instanceNetworkMarker(operationId, step);
        for (Port port : client.networking().port().list(
            Map.of("project_id", List.of(target.osProjectId()), "name", List.of(marker)))) {
          if (marker.equals(port.getName()) && target.osProjectId().equals(port.getTenantId())) {
            requireDeleteResponse(client.networking().port().delete(port.getId()));
          }
        }
        // Also finds orphan policies when creation stopped before the port response was received.
        for (NeutronNetQosPolicy policy : findPortQosPolicies(client, target.osProjectId(), marker)) {
          requireDeleteResponse(client.networking().netQosPolicy().delete(policy.getId()));
        }
      }
    }, NETWORK_EXECUTOR);
  }

  private static String instanceNetworkMarker(String operationId, InstanceNetworkStep step) {
    return "veryai:net:" + operationId
        + ":" + step.name().toLowerCase(Locale.ROOT);
  }

  public Trunk createTrunk(String region, String osProjectId, String name, String osParentPortId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    return client
        .networking()
        .trunk()
        .create(
            Builders.neutron()
                .trunk()
                .name(name)
                .adminState(true)
                .parentPort(osParentPortId)
                .build());
  }

  public NeutronNetQosPolicy createNetworkQosPolicy(
      String region, String osProjectId, String name, int maxKbps, int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
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

  public NeutronNetQosPolicy createNetworkQosPolicy(String region, String osProjectId, String name) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
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
      String osProjectId,
      String osPolicyId,
      NetQosPolicyBandwidthLimitRule.Direction direction,
      int maxKbps,
      int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    return client
        .networking()
        .netQosPolicyBandwidthLimitRule()
        .create(
            osPolicyId,
            NeutronNetQosPolicyBandwidthLimitRule.builder()
                .direction(direction)
                .maxKbps(maxKbps)
                .maxBurstKbps(maxBurstKbits)
                .build());
  }

  public void deleteNetworkQosPolicy(String region, String osProjectId, String osQosPolicyId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    client.networking().netQosPolicy().delete(osQosPolicyId);
  }

  public void updateNetworkQosPolicy(
      String region,
      String osProjectId,
      String osQosPolicyId,
      String osRuleId,
      int maxKbps,
      int maxBurstKbits) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    NetQosPolicyBandwidthLimitRule rule =
        NeutronNetQosPolicyBandwidthLimitRule.builder()
            .maxKbps(maxKbps)
            .maxBurstKbps(maxBurstKbits)
            .build();
    rule.setId(osRuleId);
    client.networking().netQosPolicyBandwidthLimitRule().update(osQosPolicyId, rule);
  }

  public void attachQosPolicyToPort(
      String region, String osProjectId, String osPortId, String osQosPolicyId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    Port port = Builders.port().qosPolicyId(osQosPolicyId).build();
    port.setId(osPortId);
    client.networking().port().update(port);
  }

  public void attachTrunkSubport(
      String region, String osProjectId, String osTrunkId, String osSubPortId, int segmentId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    client
        .networking()
        .trunk()
        .addTrunkSubport(
            osTrunkId,
            NeutronTrunkSubport.builder()
                .portId(osSubPortId)
                .segmentationId(segmentId)
                .segmentationType("vlan")
                .build());
  }

  public NetFloatingIP createPortForwardingFloatingIP(
      String region, String osProjectId, String osExtNetId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    return client
        .networking()
        .floatingip()
        .create(Builders.netFloatingIP().floatingNetworkId(osExtNetId).build());
  }

  public NetFloatingIP createFloatingIP(
      String region, String osProjectId, String osPortId, String osExtNetId) {

    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    return client
        .networking()
        .floatingip()
        .create(Builders.netFloatingIP().portId(osPortId).floatingNetworkId(osExtNetId).build());
  }

  public void rebootHard(String region, String osProjectId, String osServerId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    client.compute().servers().reboot(osServerId, RebootType.HARD);
  }

  public void rebuild(String region, String osProjectId, String osServerId, String osImageId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    client.compute().servers().rebuild(osServerId, RebuildOptions.create().image(osImageId));
  }

  public Server createVM(
      String region,
      String osProjectId,
      String name,
      String osFlavorId,
      String osImageId,
      String osPortId,
      String userData,
      String hostname) {
    ServerCreate sc =
        Builders.server()
            .name(name)
            .flavor(osFlavorId)
            .image(osImageId)
            .configDrive(true)
            .userData(Base64.getEncoder().encodeToString(userData.getBytes()))
            .addNetworkPort(osPortId)
            .hypervisorHostName(hostname)
            .addSecurityGroup("default")
            .build();

    OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
    if (client instanceof OSClientSession.OSClientSessionV3 clientSessionV3) {
      HashMap<String, String> map = new HashMap<>();
      map.put("OpenStack-API-Version", "compute 2.100");
      clientSessionV3.headers(map);
    }
    return client.compute().servers().boot(sc);
  }

  public Server getServer(String regionId, String osProjectId, String osServerId) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, osProjectId);
    Server server = client.compute().servers().get(osServerId);
    if (server != null) requireProjectOwnership(osProjectId, server.getTenantId(), "server");
    return server;
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
          metadata.put("veryai_instance_id", request.instanceId());
          metadata.put("veryai_request_id", request.requestId());
          var builder =
              Builders.server()
                  .name(request.name())
                  .image(request.osImageId())
                  .flavor(request.osFlavorId())
                  .hypervisorHostName(hypervisor.getHostname())
                  .addMetadata(metadata);
          if (request.osNetworkPortId() != null && !request.osNetworkPortId().isBlank()) {
            builder.addNetworkPort(request.osNetworkPortId());
          }
          if (request.userData() != null) {
            builder.configDrive(true).userData(Base64.getEncoder()
                .encodeToString(request.userData().getBytes(StandardCharsets.UTF_8)));
          }
          try {
            OSClient.OSClientV3 client = getOsAdminClient(region, request.target().osProjectId());
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

  public CompletionStage<NovaServer> getServer(ServerTarget target, String osServerId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        Server server = getServer(resolveServerRegion(target), target.osProjectId(), osServerId);
        return server == null ? null : toNovaServer(server);
      } catch (ResponseException error) {
        if (error.getStatus() == 404) return null;
        throw error;
      }
    }, NOVA_EXECUTOR);
  }

  public CompletionStage<ServerLookupResult> findServerByOperationId(
      ServerTarget target, String operationId) {
    return CompletableFuture.supplyAsync(
        () -> {
          requireServerText(operationId, "operationId");
          OSClient.OSClientV3 client =
              getOsAdminClient(resolveServerRegion(target), target.osProjectId());
          Map<String, String> filters = new HashMap<>();
          filters.put("project_id", target.osProjectId());
          filters.put("all_tenants", "true");
          filters.put("limit", "1000");
          NovaServer found = null;
          Set<String> markers = new HashSet<>();
          while (true) {
            List<? extends Server> page = client.compute().servers().list(filters);
            if (page == null) throw new IllegalStateException("Nova returned no server list");
            if (page.isEmpty()) break;
            for (Server server : page) {
              if (target.osProjectId().equals(server.getTenantId())
                  && server.getMetadata() != null
                  && operationId.equals(server.getMetadata().get(NOVA_OPERATION_ID))) {
                if (found != null) {
                  throw new OpenStackOperationException(
                      "Multiple Nova servers share operation " + operationId, null);
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

  public CompletionStage<Void> deleteServer(ServerTarget target, String osServerId) {
    return CompletableFuture.runAsync(
        () -> {
          OSClient.OSClientV3 client =
              getOsAdminClient(resolveServerRegion(target), target.osProjectId());
          try {
            ActionResponse response = client.compute().servers().delete(osServerId);
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
    requireServerText(target.osProjectId(), "osProjectId");
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

  /** Detail and nested-resource APIs have no project filter, so validate their owning resource. */
  private static void requireProjectOwnership(String osProjectId, String ownerProjectId, String resource) {
    requireServerText(osProjectId, "osProjectId");
    if (!osProjectId.equals(ownerProjectId)) {
      throw new OpenStackOperationException("OpenStack " + resource + " does not belong to the requested project", null);
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

  public String getVNCLink(String region, String osProjectId, String osServerId) {
    try {
      if (getServer(region, osProjectId, osServerId) == null) return null;
      OSClient.OSClientV3 client = getOsAdminClient(region, osProjectId);
      return client.compute().servers().getVNCConsole(osServerId, VNCConsole.Type.NOVNC).getURL();
    } catch (Exception e) {
      return null;
    }
  }

  public Flavor createFlavor(String region, String name, int vcpus, int ramGb, int disk) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getOsAdminDefaultProjectId());
    // Nova requires MB; callers supply GB.
    return osClient
        .compute()
        .flavors()
        .create(
            Builders.flavor()
                .name(name)
                .vcpus(vcpus)
                .ram(Math.multiplyExact(ramGb, 1024))
                .disk(disk)
                .build());
  }

  public void deleteFlavor(String region, String osProjectId, String osFlavorId) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getOsAdminDefaultProjectId());
    osClient.compute().flavors().delete(osFlavorId);
  }

  public void updateFlavorMetaData(
      String region, String osFlavorId, Map<String, String> flavorMetaData) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getOsAdminDefaultProjectId());
    osClient.compute().flavors().createAndUpdateExtraSpecs(osFlavorId, flavorMetaData);
  }

  /** Reuses the share identified by the persisted operation marker before issuing a create. */
  public CompletionStage<Share> createShare(String region, String osProjectId, ShareCreate shareCreate) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        String operationId = shareCreate.getMetadata() == null ? null : shareCreate.getMetadata().get(SHARE_OPERATION_ID);
        requireServerText(operationId, "shareOperationId");
        var shares = getOsShareClient(region, osProjectId).share().shares();
        Share existing = uniqueResource(shares.listDetails(Map.of("project_id", osProjectId, "all_tenants", "true")).stream()
            .filter(share -> osProjectId.equals(share.getProjectId()) && share.getMetadata() != null
                && operationId.equals(share.getMetadata().get(SHARE_OPERATION_ID))).toList(), "user share");
        if (existing != null) {
          if (!Objects.equals(shareCreate.getName(), existing.getName())
              || !Objects.equals(shareCreate.getSize(), existing.getSize())
              || shareCreate.getShareProto() != existing.getShareProto()
              || (!Objects.equals(shareCreate.getShareType(), existing.getShareType())
                  && !Objects.equals(shareCreate.getShareType(), existing.getShareTypeName()))) {
            throw new OpenStackOperationException("Existing share has mismatched specification", null);
          }
          return existing;
        }
        return shares.create(shareCreate);
      } catch (Exception e) {
        log.error("create share failed", e);
        throw new OpenStackOperationException("create share failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Share> getShare(String region, String osProjectId, String osShareId) {
    return CompletableFuture.supplyAsync(
        () -> getProjectShare(getOsShareClient(region, osProjectId).share().shares(), osProjectId, osShareId), SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends Share>> listShares(String region, String osProjectId) {
    return CompletableFuture.supplyAsync(
        () -> getOsShareClient(region, osProjectId).share().shares()
            .listDetails(Map.of("project_id", osProjectId, "all_tenants", "true")).stream()
            .filter(share -> osProjectId.equals(share.getProjectId())).toList(), SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends Access>> listShareAccess(
      String region, String osProjectId, String osShareId) {
    return CompletableFuture.supplyAsync(() -> {
      var shares = getOsShareClient(region, osProjectId).share().shares();
      requireProjectShare(shares, osProjectId, osShareId);
      return shares.listAccess(osShareId);
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<List<? extends ShareExportLocation>> getShareExportLocation(
      String region, String osProjectId, String osShareId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        var shares = getOsShareClient(region, osProjectId).share().shares();
        requireProjectShare(shares, osProjectId, osShareId);
        return shares.listExportLocations(osShareId);
      } catch (Exception e) {
        log.error("get share export location failed", e);
        throw new OpenStackOperationException("get share export location failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Access> grantShareAccess(
      String region, String osProjectId, String osShareId, AccessOptions accessOptions) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        var shares = getOsShareClient(region, osProjectId).share().shares();
        requireProjectShare(shares, osProjectId, osShareId);
        Access existing = uniqueResource(shares.listAccess(osShareId).stream()
            .filter(access -> accessOptions.getAccessType() == access.getAccessType()
                && accessOptions.getAccessTo().equals(access.getAccessTo())).toList(), "share access");
        if (existing != null) {
          if (existing.getAccessLevel() != accessOptions.getAccessLevel()) {
            throw new OpenStackOperationException("Existing share access has a different level", null);
          }
          return existing;
        }
        return shares.grantAccess(osShareId, accessOptions);
      } catch (Exception e) {
        log.error("grant share access failed", e);
        throw new OpenStackOperationException("grant share access failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  public CompletionStage<Access> getShareAccess(String region, String osProjectId, String osGrantAccessId) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        var shares = getOsShareClient(region, osProjectId).share().shares();
        Access access = shares.getAccess(osGrantAccessId);
        if (access != null) requireProjectShare(shares, osProjectId, access.getShareId());
        return access;
      } catch (Exception e) {
        log.error("get share access failed", e);
        throw new OpenStackOperationException("get share access failed", e);
      }
    }, SHARE_EXECUTOR);
  }

  private OSClient.OSClientV3 getOsShareClient(String region, String osProjectId) {
    return getOsAdminClient(region, osProjectId).perspective(Facing.INTERNAL);
  }

  private Share getProjectShare(SharesService shares, String osProjectId, String osShareId) {
    requireServerText(osShareId, "osShareId");
    Share share = shares.get(osShareId);
    if (share != null) requireProjectOwnership(osProjectId, share.getProjectId(), "share");
    return share;
  }

  private void requireProjectShare(SharesService shares, String osProjectId, String osShareId) {
    if (getProjectShare(shares, osProjectId, osShareId) == null) {
      throw new OpenStackOperationException("Share does not exist in the requested project", null);
    }
  }
}
