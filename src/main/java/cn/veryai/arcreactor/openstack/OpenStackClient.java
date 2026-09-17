package cn.veryai.arcreactor.openstack;

import cn.veryai.arcreactor.entity.OpenstackClusterEntity;
import cn.veryai.arcreactor.entity.RegionEntity;
import cn.veryai.arcreactor.repo.OpenstackClusterRepo;
import cn.veryai.arcreactor.repo.RegionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.types.Facing;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.*;
import org.openstack4j.model.compute.actions.RebuildOptions;
import org.openstack4j.model.identity.v3.Project;
import org.openstack4j.model.manila.Access;
import org.openstack4j.model.manila.Share;
import org.openstack4j.model.manila.ShareCreate;
import org.openstack4j.model.manila.ShareExportLocation;
import org.openstack4j.model.manila.actions.AccessOptions;
import org.openstack4j.model.network.*;
import org.openstack4j.model.network.SecurityGroup;
import org.openstack4j.model.network.builder.NetworkBuilder;
import org.openstack4j.model.network.builder.SubnetBuilder;
import org.openstack4j.model.network.ext.NetQosPolicyBandwidthLimitRule;
import org.openstack4j.model.network.ext.PortForwarding;
import org.openstack4j.openstack.OSFactory;
import org.openstack4j.openstack.internal.OSClientSession;
import org.openstack4j.openstack.networking.domain.NeutronTrunkSubport;
import org.openstack4j.openstack.networking.domain.ext.NeutronNetQosPolicy;
import org.openstack4j.openstack.networking.domain.ext.NeutronNetQosPolicyBandwidthLimitRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenStackClient {

  @Autowired private OpenstackClusterRepo clusterRepo;

  @Autowired private RegionRepo regionRepo;

  public OSClient.OSClientV3 getOsAdminClient(String region, String projectId) {
    OpenstackClusterEntity openstackClusterEntity = getOpenstackCluster(region);
    OSClient.OSClientV3 client =
        OSFactory.builderV3()
            .endpoint(openstackClusterEntity.getAuthEndpoint())
            .credentials(
                openstackClusterEntity.getAdminUsername(),
                openstackClusterEntity.getAdminPassword(),
                Identifier.byId(openstackClusterEntity.getAdminDefaultDomainId()))
            .scopeToProject(Identifier.byId(projectId))
            .authenticate();
    client.useRegion(region);
    return client;
  }

  public OpenstackClusterEntity getOpenstackCluster(String region) {
    RegionEntity regionEntity = regionRepo.findById(region);
    return clusterRepo.findById(regionEntity.getClusterId());
  }

  public void grantProjectAdminRole(String region, String projectId) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);

    OSClient.OSClientV3 osClient = getOsAdminClient(region, projectId);
    osClient
        .identity()
        .roles()
        .grantProjectUserRole(
            projectId, osClient.getToken().getUser().getId(), openstackCluster.getAdminRoleId());
  }

  public Project createProject(String region, String name) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 osClient =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    return osClient
        .identity()
        .projects()
        .create(
            Builders.project()
                .domainId(openstackCluster.getAdminDefaultDomainId())
                .name(name)
                .build());
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

  public void deletePortForwarding(String region, String floatingIpId, String portForwardingId) {
    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 client =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    client.networking().floatingip().portForwarding().delete(floatingIpId, portForwardingId);
  }

  public PortForwarding createPortForwarding(
      String region,
      String floatingIpId,
      int externalPort,
      String fixedIp,
      int internalPort,
      String internalPortId,
      NetProtocol protocol) {

    OpenstackClusterEntity openstackCluster = getOpenstackCluster(region);
    OSClient.OSClientV3 client =
        getOsAdminClient(region, openstackCluster.getAdminDefaultProjectId());
    return client
        .networking()
        .floatingip()
        .portForwarding()
        .create(
            floatingIpId,
            Builders.portForwarding()
                .externalPort(externalPort)
                .internalIpAddress(fixedIp)
                .internalPort(internalPort)
                .internalPortId(internalPortId)
                .protocol(protocol.name().toLowerCase())
                .build());
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
        .create(Builders.router().name(name).adminStateUp(true).externalGateway(extNetId).build());
  }

  public Network createNetwork(String regionId, String projectId, String name) {
    OSClient.OSClientV3 client = getOsAdminClient(regionId, projectId);
    return client
        .networking()
        .network()
        .create(Builders.network().name(name).adminStateUp(true).build());
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

  public Port createPort(String region, String projectId, String netId, String subnetId) {
    OSClient.OSClientV3 client = getOsAdminClient(region, projectId);
    return client
        .networking()
        .port()
        .create(Builders.port().fixedIp(null, subnetId).networkId(netId).build());
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

  public Share createShare(String region, String projectId, ShareCreate shareCreate) {
    try {
      return getOsShareClient(region, projectId).share().shares().create(shareCreate);
    } catch (Exception e) {
      log.error("create share failed", e);
      throw new OpenStackOperationException("create share failed", e);
    }
  }

  public List<? extends ShareExportLocation> getShareExportLocation(
      String region, String projectId, String shareId) {
    try {
      return getOsShareClient(region, projectId).share().shares().listExportLocations(shareId);
    } catch (Exception e) {
      log.error("get share export location failed", e);
      return List.of();
    }
  }

  public Access grantShareAccess(
      String region, String projectId, String shareId, AccessOptions accessOptions) {
    try {
      return getOsShareClient(region, projectId)
          .share()
          .shares()
          .grantAccess(shareId, accessOptions);
    } catch (Exception e) {
      log.error("grant share access failed", e);
      throw new OpenStackOperationException("grant share access failed", e);
    }
  }

  public Access getShareAccess(String region, String projectId, String accessId) {
    try {
      return getOsShareClient(region, projectId).share().shares().getAccess(accessId);
    } catch (Exception e) {
      log.error("get share access failed", e);
      throw new OpenStackOperationException("get share access failed", e);
    }
  }

  private OSClient.OSClientV3 getOsShareClient(String region, String projectId) {
    return getOsAdminClient(region, projectId).perspective(Facing.INTERNAL);
  }
}
