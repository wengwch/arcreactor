package cn.veryai.arcreactor.openstack;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingResources;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
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
import java.time.Instant;
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
  private static final String SHARE_OPERATION_ID = "arcreactor_share_operation_id";
  private static final Executor NOVA_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-nova").start(task);
  private static final Executor SHARE_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-share").start(task);
  private static final Executor PROJECT_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-project").start(task);
  private static final Executor NETWORK_EXECUTOR =
      task -> Thread.ofVirtual().name("openstack-network").start(task);

  public record ManagedNetworkResource(String id) implements Serializable {
    public ManagedNetworkResource {
      if (id == null || id.isBlank()) throw new IllegalStateException("Missing network resource ID");
    }
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

  private record AdminClientKey(String clusterId, String projectId) {}

  @Autowired private OpenstackClusterRepo clusterRepo;

  @Autowired private RegionRepo regionRepo;

  @Autowired private HypervisorHostRepo hypervisorHostRepo;

  @Autowired private SysNetworkRepo sysNetworkRepo;

  public OSClient.OSClientV3 getOsAdminClient(String region, String projectId) {
    return getOsClusterAdminClient(getOpenstackCluster(region), projectId).useRegion(region);
  }

  private OSClient.OSClientV3 getOsClusterAdminClient(OpenstackClusterEntity cluster, String projectId) {
    Token token =
        adminTokenCache
            .asMap()
            .compute(
                new AdminClientKey(cluster.getId(), projectId),
                (key, cachedToken) -> {
                  if (cachedToken != null
                      && cachedToken.getExpires() != null
                      && cachedToken.getExpires().getTime()
                          > System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MILLIS) {
                    return cachedToken;
                  }
                  return authenticateAdmin(cluster, key.projectId());
                });
    // Sessions have mutable headers/perspective and thread-local state; only share the token.
    return OSFactory.clientFromToken(token).useRegion(cluster.getAdminDefaultRegion());
  }

  private Token authenticateAdmin(OpenstackClusterEntity cluster, String projectId) {
    return OSFactory.builderV3()
        .endpoint(cluster.getAuthEndpoint())
        .credentials(
            cluster.getAdminUsername(),
            cluster.getAdminPassword(),
            Identifier.byId(cluster.getAdminDefaultDomainId()))
        .scopeToProject(Identifier.byId(projectId))
        .authenticate()
        .getToken();
  }

  public OpenstackClusterEntity getOpenstackCluster(String region) {
    RegionEntity regionEntity = regionRepo.findById(region);
    return clusterRepo.findById(regionEntity.getClusterId());
  }

  public List<? extends Hypervisor> listHypervisors(String region) {
    return getOsHypervisorClient(region).compute().hypervisors().list();
  }

  public Hypervisor getHypervisor(String region, String hypervisorId) {
    OSClient.OSClientV3 osClient = getOsHypervisorClient(region);
    try {
      return osClient.compute().hypervisors().get(hypervisorId);
    } catch (ResponseException exception) {
      if (exception.getStatus() == 404) return null;
      throw exception;
    }
  }

  private OSClient.OSClientV3 getOsHypervisorClient(String region) {
    OpenstackClusterEntity cluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient = getOsAdminClient(region, cluster.getAdminDefaultProjectId());
    if (osClient instanceof OSClientSession.OSClientSessionV3 clientSessionV3) {
      HashMap<String, String> map = new HashMap<>();
      map.put("OpenStack-API-Version", "compute 2.87");
      clientSessionV3.headers(map);
    }
    return osClient;
  }

  public CompletionStage<Void> grantProjectAdminRole(String clusterId, String projectId) {
    return CompletableFuture.runAsync(() -> {
      OpenstackClusterEntity cluster = clusterRepo.findById(clusterId);
      requireServerText(cluster.getAdminRoleId(), "adminRoleId");
      // Authenticate in the existing admin project before granting access to the new project.
      OSClient.OSClientV3 osClient = getOsClusterAdminClient(cluster, cluster.getAdminDefaultProjectId());
      ActionResponse response = osClient.identity().roles().grantProjectUserRole(
          projectId, osClient.getToken().getUser().getId(), cluster.getAdminRoleId());
      if (response == null || !response.isSuccess()) {
        throw new IllegalStateException("Project administrator role assignment was not confirmed");
      }
    }, PROJECT_EXECUTOR);
  }

  /** Reuses a project with the same name in the configured domain, creating it only when absent. */
  public CompletionStage<Project> createProject(String clusterId, String name, String operationId) {
    return CompletableFuture.supplyAsync(() -> {
      OpenstackClusterEntity cluster = clusterRepo.findById(clusterId);
      OSClient.OSClientV3 osClient = getOsClusterAdminClient(cluster, cluster.getAdminDefaultProjectId());
      String domainId = cluster.getAdminDefaultDomainId();
      var projects = osClient.identity().projects();
      Project existing = projects.getByName(name, domainId);
      if (existing != null) return existing;
      return projects.create(Builders.project()
          .domainId(domainId).name(name).enabled(true)
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

  /** Reads existing rule IDs and external ports for floating IP activation. */
  public CompletionStage<List<PortForwardingSnapshot>> listPortForwardings(String region, String floatingIpId) {
    return CompletableFuture.supplyAsync(() -> getOsForwardingClient(region).networking().floatingip()
        .portForwarding().list(floatingIpId).stream()
        .map(rule -> new PortForwardingSnapshot(rule.getId(), rule.getExternalPort()))
        .toList(), NETWORK_EXECUTOR);
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

  /** Directly creates TCP and UDP rules; markers are retained for creation recovery. */
  public CompletionStage<ForwardingResources> createPortForwardings(FloatingIpIdentity identity,
      String claimId, ForwardingSpec spec, int externalPort) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsForwardingClient(identity.regionId()).networking().floatingip().portForwarding();
      String tcpId = createPortForwarding(service, identity.floatingIpId(), externalPort, spec, NetProtocol.TCP,
          forwardingMarker(claimId, NetProtocol.TCP));
      String udpId = createPortForwarding(service, identity.floatingIpId(), externalPort, spec, NetProtocol.UDP,
          forwardingMarker(claimId, NetProtocol.UDP));
      return new ForwardingResources(identity.floatingIp(), externalPort, tcpId, udpId);
    }, NETWORK_EXECUTOR);
  }

  /** Recovery reuses matching rules on the reserved port and creates only missing protocols. */
  public CompletionStage<ForwardingResources> recoverPortForwardings(FloatingIpIdentity identity,
      String claimId, ForwardingSpec spec, int externalPort) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsForwardingClient(identity.regionId()).networking().floatingip().portForwarding();
      List<? extends PortForwarding> forwardings = service.list(identity.floatingIpId());
      String tcpMarker = forwardingMarker(claimId, NetProtocol.TCP);
      String udpMarker = forwardingMarker(claimId, NetProtocol.UDP);
      // Validate both protocols before creating anything during recovery.
      String tcpId = findExistingForwardingId(forwardings, externalPort, spec, NetProtocol.TCP, tcpMarker);
      String udpId = findExistingForwardingId(forwardings, externalPort, spec, NetProtocol.UDP, udpMarker);
      if (tcpId == null) {
        tcpId = createPortForwarding(service, identity.floatingIpId(), externalPort, spec, NetProtocol.TCP, tcpMarker);
      }
      if (udpId == null) {
        udpId = createPortForwarding(service, identity.floatingIpId(), externalPort, spec, NetProtocol.UDP, udpMarker);
      }
      return new ForwardingResources(identity.floatingIp(), externalPort, tcpId, udpId);
    }, NETWORK_EXECUTOR);
  }

  private static String findExistingForwardingId(List<? extends PortForwarding> forwardings,
      int externalPort, ForwardingSpec spec, NetProtocol protocol, String marker) {
    List<? extends PortForwarding> matches = forwardings.stream()
        .filter(rule -> marker.equals(rule.getDescription())).toList();
    if (matches.size() > 1) throw new IllegalStateException("Duplicate forwarding operation marker");
    if (matches.isEmpty()) return null;
    PortForwarding existing = matches.getFirst();
    if (existing.getExternalPort() != externalPort || existing.getInternalPort() != spec.internalPort()
        || !spec.internalPortId().equals(existing.getInternalPortId())
        || !spec.fixedIp().equals(existing.getInternalIpAddress())
        || !protocol.name().equalsIgnoreCase(existing.getProtocol())) {
      throw new IllegalStateException("Forwarding operation marker has mismatched parameters");
    }
    if (existing.getId() == null || existing.getId().isBlank()) {
      throw new IllegalStateException("Missing forwarding ID");
    }
    return existing.getId();
  }

  private String createPortForwarding(PortForwardingService service, String floatingIpId,
      int externalPort, ForwardingSpec spec, NetProtocol protocol, String marker) {
    PortForwarding created = service.create(floatingIpId, Builders.portForwarding()
        .externalPort(externalPort).internalIpAddress(spec.fixedIp()).internalPort(spec.internalPort())
        .internalPortId(spec.internalPortId()).protocol(protocol.name().toLowerCase(Locale.ROOT))
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

  /** Resume the instance's own group and missing rules after recovery, before creating any ports. */
  public CompletionStage<String> prepareInstanceSecurityGroup(ServerTarget target, String operationId,
      List<Integer> internalPorts, Instant deadline) {
    List<Integer> ports = List.copyOf(internalPorts);
    if (ports.stream().anyMatch(port -> port < 1 || port > 65535)) {
      throw new IllegalArgumentException("Invalid instance security group port");
    }
    String marker = instanceSecurityGroupMarker(operationId);
    return CompletableFuture.supplyAsync(() -> {
      requireSecurityGroupDeadline(deadline);
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.projectId());
      List<? extends SecurityGroup> matches = findInstanceSecurityGroups(client, target.projectId(), marker);
      if (matches.size() > 1) {
        throw new OpenStackOperationException("Duplicate instance security group marker", null);
      }
      SecurityGroup group = matches.isEmpty() ? null : matches.getFirst();
      if (group == null) {
        requireSecurityGroupDeadline(deadline);
        group = client.networking().securitygroup().create(Builders.securityGroup()
            .name(marker).description(marker).tenantId(target.projectId()).build());
      }
      if (group == null || group.getId() == null || group.getId().isBlank()
          || !target.projectId().equals(group.getTenantId()) || !marker.equals(group.getDescription())) {
        throw new OpenStackOperationException("Missing or mismatched instance security group", null);
      }
      for (int port : ports) {
        for (NetProtocol protocol : List.of(NetProtocol.TCP, NetProtocol.UDP)) {
          ensureInstanceSecurityRule(client, group.getId(), port, protocol, marker, deadline);
        }
      }
      return group.getId();
    }, NETWORK_EXECUTOR);
  }

  private void ensureInstanceSecurityRule(OSClient.OSClientV3 client, String groupId, int internalPort,
      NetProtocol protocol, String groupMarker, Instant deadline) {
    var service = client.networking().securityrule();
    if (service.list(Map.of("security_group_id", groupId)).stream()
        .anyMatch(rule -> allowsInstancePort(rule, groupId, internalPort, protocol))) return;
    requireSecurityGroupDeadline(deadline);
    try {
      SecurityGroupRule created = service.create(Builders.securityGroupRule().securityGroupId(groupId)
          .protocol(protocol.name().toLowerCase(Locale.ROOT)).direction("ingress").ethertype("IPv4")
          .portRangeMin(internalPort).portRangeMax(internalPort).remoteIpPrefix("0.0.0.0/0")
          .description(groupMarker + ":" + protocol.name().toLowerCase(Locale.ROOT) + ":" + internalPort).build());
      if (created == null || created.getId() == null || created.getId().isBlank()
          || !allowsInstancePort(created, groupId, internalPort, protocol)) {
        throw new OpenStackOperationException("Missing or mismatched instance security rule", null);
      }
    } catch (ResponseException error) {
      // An earlier request may have completed while recovery was querying the group's rules.
      if (error.getStatus() != 409 || service.list(Map.of("security_group_id", groupId)).stream()
          .noneMatch(rule -> allowsInstancePort(rule, groupId, internalPort, protocol))) throw error;
    }
  }

  private static void requireSecurityGroupDeadline(Instant deadline) {
    if (!Instant.now().isBefore(deadline)) {
      throw new OpenStackOperationException("Instance security group preparation deadline exceeded", null);
    }
  }

  private List<? extends SecurityGroup> findInstanceSecurityGroups(OSClient.OSClientV3 client,
      String projectId, String marker) {
    return client.networking().securitygroup().list(Map.of("project_id", projectId)).stream()
        .filter(group -> projectId.equals(group.getTenantId()) && marker.equals(group.getDescription())).toList();
  }

  private static String instanceSecurityGroupMarker(String operationId) {
    requireServerText(operationId, "operationId");
    return "arcreactor-" + UUID.nameUUIDFromBytes(operationId.getBytes(StandardCharsets.UTF_8)) + "-security-group";
  }

  /** Called after instance ports have been removed. Also finds groups whose create response was lost. */
  public CompletionStage<Void> deleteInstanceSecurityGroup(ServerTarget target, String operationId) {
    return CompletableFuture.runAsync(() -> {
      OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.projectId());
      for (SecurityGroup group : findInstanceSecurityGroups(client, target.projectId(), instanceSecurityGroupMarker(operationId))) {
        requireDeleteResponse(client.networking().securitygroup().delete(group.getId()));
      }
    }, NETWORK_EXECUTOR);
  }

  /** Queries recorded rule IDs before deletion; missing IDs leave deletion incomplete. */
  public CompletionStage<Boolean> deletePortForwardings(FloatingIpIdentity identity, ForwardingResources resources) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsForwardingClient(identity.regionId()).networking().floatingip().portForwarding();
      boolean tcpDeleted = deleteManagedPortForwarding(service, identity.floatingIpId(), resources.tcpForwardingId());
      boolean udpDeleted = deleteManagedPortForwarding(service, identity.floatingIpId(), resources.udpForwardingId());
      return tcpDeleted && udpDeleted;
    }, NETWORK_EXECUTOR);
  }

  /** Independently resumes interrupted deletion by checking each recorded rule before removing it. */
  public CompletionStage<Boolean> recoverPortForwardingDeletion(FloatingIpIdentity identity,
      ForwardingResources resources) {
    return CompletableFuture.supplyAsync(() -> {
      var service = getOsForwardingClient(identity.regionId()).networking().floatingip().portForwarding();
      boolean complete = true;
      for (String ruleId : new String[] {resources.tcpForwardingId(), resources.udpForwardingId()}) {
        if (ruleId == null || ruleId.isBlank()) {
          complete = false;
          continue;
        }
        try {
          if (service.get(identity.floatingIpId(), ruleId) != null) {
            requireDeleteResponse(service.delete(identity.floatingIpId(), ruleId));
          }
        } catch (ResponseException error) {
          if (error.getStatus() != 404) throw error;
        }
      }
      return complete;
    }, NETWORK_EXECUTOR);
  }

  private boolean deleteManagedPortForwarding(PortForwardingService service, String floatingIpId,
      String ruleId) {
    if (ruleId == null || ruleId.isBlank()) return false;
    try {
      if (service.get(floatingIpId, ruleId) == null) return true;
      requireDeleteResponse(service.delete(floatingIpId, ruleId));
    } catch (ResponseException error) {
      if (error.getStatus() != 404) throw error;
    }
    return true;
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

  private static boolean allowsInstancePort(SecurityGroupRule rule, String groupId, int port, NetProtocol protocol) {
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

  /** Resumes the user network by reusing each confirmed resource before creating a missing step. */
  public CompletionStage<NetworkResources> createUserNetwork(NetworkSpec spec, String projectId, String name) {
    return CompletableFuture.supplyAsync(() -> {
      String regionId = spec.regionId();
      String localNetworkId = resolveUserLocalNetwork(regionId, projectId);

      Network network = uniqueResource(listNetworks(regionId, projectId).stream()
          .filter(item -> projectId.equals(item.getTenantId()) && name.equals(item.getName())).toList(),
          "user network");
      if (network == null) network = createNetwork(regionId, projectId, name);
      if (network == null) throw new IllegalStateException("Neutron returned no network");
      if (!projectId.equals(network.getTenantId()) || network.isRouterExternal() || network.isShared()) {
        throw new OpenStackOperationException("User network has mismatched ownership or visibility", null);
      }
      String networkId = networkResourceId(network.getId());

      Subnet subnet = uniqueResource(listSubnets(regionId, projectId).stream()
          .filter(item -> projectId.equals(item.getTenantId()) && (name + "-subnet").equals(item.getName())).toList(),
          "user subnet");
      if (subnet == null) subnet = createSubnet(regionId, projectId, networkId, name + "-subnet",
          spec.ipv4CIDR(), spec.dns(), spec.gateway(), Map.of());
      if (subnet == null) throw new IllegalStateException("Neutron returned no subnet");
      if (!projectId.equals(subnet.getTenantId()) || !networkId.equals(subnet.getNetworkId())
          || !spec.ipv4CIDR().equals(subnet.getCidr()) || subnet.getIpVersion() != IPVersionType.V4
          || !subnet.isDHCPEnabled() || !userNetworkDns(spec.dns()).equals(
              subnet.getDnsNames() == null ? Set.of() : new HashSet<>(subnet.getDnsNames()))
          || (spec.gateway() != null && !spec.gateway().equals(subnet.getGateway()))) {
        throw new OpenStackOperationException("User subnet has mismatched ownership or network settings", null);
      }
      String subnetId = networkResourceId(subnet.getId());

      Router router = uniqueResource(listRouters(regionId, projectId).stream()
          .filter(item -> projectId.equals(item.getTenantId()) && (name + "-router").equals(item.getName())).toList(),
          "user router");
      if (router == null) router = createRouter(regionId, projectId, name + "-router", localNetworkId);
      if (router == null) throw new IllegalStateException("Neutron returned no router");
      if (!projectId.equals(router.getTenantId()) || router.getExternalGatewayInfo() == null
          || !localNetworkId.equals(router.getExternalGatewayInfo().getNetworkId())) {
        throw new OpenStackOperationException("User router has mismatched ownership or LOCAL network gateway", null);
      }
      String routerId = networkResourceId(router.getId());

      Port existing = findUserRouterInterface(regionId, projectId, routerId, networkId, subnetId);
      String routerPortId;
      if (existing != null) {
        routerPortId = networkResourceId(existing.getId());
      } else {
        RouterInterface attached = attachRouterInterface(regionId, projectId, routerId, subnetId);
        if (attached == null || !subnetId.equals(attached.getSubnetId()) || !routerId.equals(attached.getId())) {
          throw new IllegalStateException("Neutron did not confirm the user router interface");
        }
        routerPortId = networkResourceId(attached.getPortId());
      }
      return new NetworkResources(localNetworkId, networkId, subnetId, routerId, routerPortId);
    }, NETWORK_EXECUTOR);
  }

  private Port findUserRouterInterface(String regionId, String projectId, String routerId,
      String networkId, String subnetId) {
    Port port = uniqueResource(listPorts(regionId, projectId).stream()
        .filter(item -> routerId.equals(item.getDeviceId()) && item.getFixedIps() != null
            && item.getFixedIps().stream().anyMatch(ip -> subnetId.equals(ip.getSubnetId())))
        .toList(), "user router interface");
    if (port != null && (!projectId.equals(port.getTenantId()) || !networkId.equals(port.getNetworkId())
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

  private String resolveUserLocalNetwork(String regionId, String projectId) {
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
    Network network = getNetwork(regionId, projectId, local.getOsNetId());
    if (network == null || !network.isRouterExternal() || !network.isShared()) {
      throw new OpenStackOperationException("Preset LOCAL network is not a shared external Neutron network", null);
    }
    return networkResourceId(network.getId());
  }

  private static String networkResourceId(String id) {
    if (id == null || id.isBlank()) throw new IllegalStateException("Missing network resource ID");
    return id;
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
    return createPort(region, projectId, netId, subnetId, PortBandwidthLimit.UNLIMITED);
  }

  public Port createPort(String region, String projectId, String netId, String subnetId,
      PortBandwidthLimit bandwidthLimit) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return createPortWithQos(client, Builders.port().name("arcreactor-" + UUID.randomUUID())
        .tenantId(projectId).fixedIp(null, subnetId).networkId(netId).build(), bandwidthLimit);
  }

  /** Creates the policy and both bandwidth rules, then binds them in the port create request. */
  private Port createPortWithQos(OSClient.OSClientV3 client, Port request, PortBandwidthLimit bandwidthLimit) {
    return createPortWithQos(client, request, bandwidthLimit, true);
  }

  private Port createPortWithQos(OSClient.OSClientV3 client, Port request,
      PortBandwidthLimit bandwidthLimit, boolean cleanupOnFailure) {
    Objects.requireNonNull(bandwidthLimit, "bandwidthLimit");
    requireServerText(request.getName(), "portName");
    requireServerText(request.getTenantId(), "portProjectId");
    List<NeutronNetQosPolicy> policies = findPortQosPolicies(client, request.getTenantId(), request.getName());
    if (policies.size() > 1) {
      throw new OpenStackOperationException("Duplicate port QoS policy marker", null);
    }
    NeutronNetQosPolicy policy = policies.isEmpty() ? null : policies.getFirst();
    if (policy == null) {
      String marker = portQosMarker(request.getName());
      policy = (NeutronNetQosPolicy) client.networking().netQosPolicy().create(
          NeutronNetQosPolicy.builder().name(marker).description(marker)
              .tenantId(request.getTenantId()).shared(false).isDefault(false).build());
      if (policy == null) throw new IllegalStateException("Neutron returned no port QoS policy");
    }
    requireServerText(policy.getId(), "portQosPolicyId");
    Port port;
    try {
      ensurePortBandwidthRules(client, policy.getId(), bandwidthLimit, true);
      port = client.networking().port().create(request.toBuilder().qosPolicyId(policy.getId()).build());
    } catch (ResponseException error) {
      int status = error.getStatus();
      if (status >= 400 && status < 500 && status != 408 && status != 429) {
        if (cleanupOnFailure) {
          try {
            requireDeleteResponse(client.networking().netQosPolicy().delete(policy.getId()));
          } catch (RuntimeException cleanupError) {
            error.addSuppressed(cleanupError);
          }
        } else {
          log.warn("Instance port creation failed; QoS retained for manual deletion: projectId={} portName={} policyId={} status={}",
              request.getTenantId(), request.getName(), policy.getId(), status);
        }
      }
      // An uncertain write may already have created a bound port; leave cleanup to reconciliation.
      throw error;
    }
    if (port == null) throw new IllegalStateException("Neutron returned no port");
    if (!policy.getId().equals(port.getQosPolicyId())) {
      throw new OpenStackOperationException("Created port is not bound to its QoS policy", null);
    }
    return port;
  }

  /** Missing rules are created only during the recorded create attempt; recovery only verifies them. */
  private void ensurePortBandwidthRules(OSClient.OSClientV3 client, String policyId,
      PortBandwidthLimit bandwidthLimit, boolean allowCreate) {
    var service = client.networking().netQosPolicyBandwidthLimitRule();
    List<? extends NetQosPolicyBandwidthLimitRule> rules = service.list(policyId);
    if (!bandwidthLimit.limited()) {
      if (!rules.isEmpty()) {
        throw new OpenStackOperationException("Unlimited port QoS policy contains bandwidth limit rules", null);
      }
      if (allowCreate) {
        log.info("Skipping QoS bandwidth rules: policyId={} maxKbps={} maxBurstKbits={} (unlimited)",
            policyId, bandwidthLimit.maxKbps(), bandwidthLimit.maxBurstKbits());
      }
      return;
    }
    for (NetQosPolicyBandwidthLimitRule.Direction direction : NetQosPolicyBandwidthLimitRule.Direction.values()) {
      List<? extends NetQosPolicyBandwidthLimitRule> matches = rules.stream()
          .filter(rule -> Objects.requireNonNullElse(rule.getDirection(),
              NetQosPolicyBandwidthLimitRule.Direction.egress) == direction).toList();
      if (matches.size() > 1) {
        throw new OpenStackOperationException("Duplicate port bandwidth limit direction", null);
      }
      NetQosPolicyBandwidthLimitRule rule = matches.isEmpty() ? null : matches.getFirst();
      if (rule == null) {
        if (!allowCreate) {
          throw new OpenStackOperationException("Port QoS policy is missing a bandwidth limit rule", null);
        }
        log.info("Creating QoS bandwidth rule: policyId={} direction={} maxKbps={} maxBurstKbits={}",
            policyId, direction, bandwidthLimit.maxKbps(), bandwidthLimit.maxBurstKbits());
        rule = service.create(policyId, NeutronNetQosPolicyBandwidthLimitRule.builder()
            .direction(direction).maxKbps(bandwidthLimit.maxKbps())
            .maxBurstKbps(bandwidthLimit.maxBurstKbits()).build());
        if (rule == null) throw new IllegalStateException("Neutron returned no bandwidth limit rule");
      }
      requireServerText(rule.getId(), "bandwidthLimitRuleId");
      if (!Objects.equals(rule.getMaxKbps(), bandwidthLimit.maxKbps())
          || Objects.requireNonNullElse(rule.getMaxBurstKbps(), 0) != bandwidthLimit.maxBurstKbits()
          || Objects.requireNonNullElse(rule.getDirection(), NetQosPolicyBandwidthLimitRule.Direction.egress) != direction) {
        throw new OpenStackOperationException("Port bandwidth limit does not match the create command", null);
      }
    }
  }

  private List<NeutronNetQosPolicy> findPortQosPolicies(OSClient.OSClientV3 client,
      String projectId, String portName) {
    return client.networking().netQosPolicy().list().stream()
        .filter(policy -> isPortQosPolicy(policy, projectId, portName))
        .map(policy -> (NeutronNetQosPolicy) policy).toList();
  }

  private static boolean isPortQosPolicy(NetQosPolicy policy, String projectId, String portName) {
    return policy != null && projectId.equals(policy.getTenantId())
        && portQosMarker(portName).equals(policy.getDescription());
  }

  private static String portQosMarker(String portName) {
    return portName + "-qos";
  }

  private enum InstanceNetworkStep { PARENT_PORT, SUB_PORT, TRUNK }

  /** Prepares only the parent port and its QoS policy. */
  public CompletionStage<PreparedNetwork> prepareInstanceParentPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String securityGroupId, boolean allowCreate) {
    return prepareInstanceNetworkStep(target, operationId, requirement, securityGroupId,
        InstanceNetworkStep.PARENT_PORT, PreparedNetwork.empty(), allowCreate);
  }

  /** Prepares only the subport and its QoS policy, using the persisted parent MAC address. */
  public CompletionStage<PreparedNetwork> prepareInstanceSubPort(ServerTarget target,
      String operationId, NetworkRequirement requirement, String securityGroupId, PreparedNetwork prepared,
      boolean allowCreate) {
    return prepareInstanceNetworkStep(target, operationId, requirement, securityGroupId,
        InstanceNetworkStep.SUB_PORT, prepared, allowCreate);
  }

  /** Prepares only the trunk, using the persisted parent and subport IDs. */
  public CompletionStage<PreparedNetwork> prepareInstanceTrunk(ServerTarget target,
      String operationId, NetworkRequirement requirement, PreparedNetwork prepared, boolean allowCreate) {
    return prepareInstanceNetworkStep(target, operationId, requirement, null,
        InstanceNetworkStep.TRUNK, prepared, allowCreate);
  }

  /** Recovery passes allowCreate=false and only discovers the resource for the current durable stage. */
  private CompletionStage<PreparedNetwork> prepareInstanceNetworkStep(ServerTarget target,
      String operationId, NetworkRequirement requirement, String securityGroupId, InstanceNetworkStep step,
      PreparedNetwork prepared, boolean allowCreate) {
    if (!requirement.resolved()) throw new IllegalArgumentException("Network dependencies must be resolved");
    Objects.requireNonNull(prepared, "prepared");
    if (step != InstanceNetworkStep.TRUNK) requireServerText(securityGroupId, "securityGroupId");
    if (step != InstanceNetworkStep.PARENT_PORT) {
      requireServerText(prepared.parentPortId(), "parentPortId");
      requireServerText(prepared.parentMacAddress(), "parentMacAddress");
      requireServerText(prepared.parentFixedIp(), "parentFixedIp");
    }
    if (step == InstanceNetworkStep.TRUNK) {
      requireServerText(prepared.subPortId(), "subPortId");
      requireServerText(prepared.subFixedIp(), "subFixedIp");
    }
    return CompletableFuture.supplyAsync(() -> {
      try {
        OSClient.OSClientV3 client = getOsAdminClient(resolveServerRegion(target), target.projectId());
        return ensureInstanceNetworkStep(target, operationId, requirement, securityGroupId,
            step, prepared, allowCreate, client);
      } catch (ResponseException error) {
        int status = error.getStatus();
        if (status >= 400 && status < 500 && status != 408 && status != 429) {
          throw new OpenStackOperationException("Neutron rejected instance "
              + step.name().toLowerCase(Locale.ROOT) + " preparation", error);
        }
        throw error;
      }
    }, NETWORK_EXECUTOR);
  }

  private PreparedNetwork ensureInstanceNetworkStep(ServerTarget target, String operationId,
      NetworkRequirement requirement, String securityGroupId, InstanceNetworkStep step, PreparedNetwork prepared,
      boolean allowCreate, OSClient.OSClientV3 client) {
    String marker = instanceNetworkMarker(operationId, step);
    if (step == InstanceNetworkStep.TRUNK) {
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
    boolean parent = step == InstanceNetworkStep.PARENT_PORT;
    String networkId = parent ? requirement.parentNetworkId() : requirement.subNetworkId();
    String subnetId = parent ? requirement.parentSubnetId() : requirement.subSubnetId();
    PortBandwidthLimit bandwidthLimit = parent
        ? requirement.parentPortBandwidthLimit() : requirement.subPortBandwidthLimit();
    List<? extends Port> matches = client.networking().port().list(
        PortListOptions.create().tenantId(target.projectId()).name(marker)).stream()
        .filter(port -> marker.equals(port.getName()) && target.projectId().equals(port.getTenantId()))
        .toList();
    if (matches.size() > 1) throw new IllegalStateException("Duplicate instance port marker");
    Port port = matches.isEmpty() ? null : matches.getFirst();
    if (port == null && allowCreate) {
      var builder = Builders.port().name(marker).tenantId(target.projectId())
          .networkId(networkId).fixedIp(null, subnetId).adminState(true)
          .portSecurityEnabled(true).securityGroup(securityGroupId);
      if (!parent) builder.macAddress(prepared.parentMacAddress());
      port = createPortWithQos(client, builder.build(), bandwidthLimit, false);
    }
    if (port == null) return null;
    if (!Boolean.TRUE.equals(port.isPortSecurityEnabled()) || port.getSecurityGroups() == null
        || !Set.of(securityGroupId).equals(new HashSet<>(port.getSecurityGroups()))) {
      throw new OpenStackOperationException("Instance port must use its dedicated security group", null);
    }
    if (!networkId.equals(port.getNetworkId()) || port.getFixedIps() == null
        || (!parent && !prepared.parentMacAddress().equalsIgnoreCase(port.getMacAddress()))) {
      throw new OpenStackOperationException("Instance port has mismatched network or MAC address", null);
    }
    requireServerText(port.getId(), "portId");
    requireServerText(port.getMacAddress(), "portMacAddress");
    requireServerText(port.getQosPolicyId(), "portQosPolicyId");
    if (!isPortQosPolicy(client.networking().netQosPolicy().get(port.getQosPolicyId()),
        target.projectId(), marker)) {
      throw new OpenStackOperationException("Instance port has a mismatched QoS policy", null);
    }
    ensurePortBandwidthRules(client, port.getQosPolicyId(), bandwidthLimit, false);
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
      String trunkMarker = instanceNetworkMarker(operationId, InstanceNetworkStep.TRUNK);
      for (Trunk trunk : client.networking().trunk().list()) {
        if (!trunkMarker.equals(trunk.getDescription()) || !target.projectId().equals(trunk.getTenantId())) continue;
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
            PortListOptions.create().tenantId(target.projectId()).name(marker))) {
          if (marker.equals(port.getName()) && target.projectId().equals(port.getTenantId())) {
            requireDeleteResponse(client.networking().port().delete(port.getId()));
          }
        }
        // Also finds orphan policies when creation stopped before the port response was received.
        for (NeutronNetQosPolicy policy : findPortQosPolicies(client, target.projectId(), marker)) {
          requireDeleteResponse(client.networking().netQosPolicy().delete(policy.getId()));
        }
      }
    }, NETWORK_EXECUTOR);
  }

  private static String instanceNetworkMarker(String operationId, InstanceNetworkStep step) {
    return "arcreactor-" + UUID.nameUUIDFromBytes(operationId.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        + "-" + step.name().toLowerCase(Locale.ROOT);
  }

  public Port createSubPort(
      String region, String projectId, String netId, String subnetId, String macAddress) {
    return createSubPort(region, projectId, netId, subnetId, macAddress, PortBandwidthLimit.UNLIMITED);
  }

  public Port createSubPort(String region, String projectId, String netId, String subnetId,
      String macAddress, PortBandwidthLimit bandwidthLimit) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return createPortWithQos(client, Builders.port().name("arcreactor-" + UUID.randomUUID())
        .tenantId(projectId).fixedIp(null, subnetId).networkId(netId).macAddress(macAddress).build(), bandwidthLimit);
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
    return CompletableFuture.supplyAsync(() -> {
      try {
        Server server = getServer(resolveServerRegion(target), target.projectId(), serverId);
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

  public Flavor createFlavor(String region, String name, int vcpus, int ramGb, int disk) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
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

  /** Reuses the share identified by the persisted operation marker before issuing a create. */
  public CompletionStage<Share> createShare(String region, String projectId, ShareCreate shareCreate) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        String operationId = shareCreate.getMetadata() == null ? null : shareCreate.getMetadata().get(SHARE_OPERATION_ID);
        requireServerText(operationId, "shareOperationId");
        var shares = getOsShareClient(region, projectId).share().shares();
        Share existing = uniqueResource(shares.listDetails().stream()
            .filter(share -> projectId.equals(share.getProjectId()) && share.getMetadata() != null
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
        var shares = getOsShareClient(region, projectId).share().shares();
        Access existing = uniqueResource(shares.listAccess(shareId).stream()
            .filter(access -> accessOptions.getAccessType() == access.getAccessType()
                && accessOptions.getAccessTo().equals(access.getAccessTo())).toList(), "share access");
        if (existing != null) {
          if (existing.getAccessLevel() != accessOptions.getAccessLevel()) {
            throw new OpenStackOperationException("Existing share access has a different level", null);
          }
          return existing;
        }
        return shares.grantAccess(shareId, accessOptions);
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
