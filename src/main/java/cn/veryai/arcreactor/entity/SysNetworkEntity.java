package cn.veryai.arcreactor.entity;

import cn.veryai.arcreactor.enums.SysNetworkType;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.openstack4j.model.network.NetworkType;

import java.util.HashMap;
import java.util.Map;

@FieldNameConstants(innerTypeName = "F")
@Data
public class SysNetworkEntity {

  private String id;
  private String name;
  private String description;
  private String ipv4CIDR;
  private String gateway;
  private String dns;
  private String segmentId;
  private SysNetworkType sysNetworkType;
  private String physicalNetwork;
  private boolean routerExternally;
  private boolean shared;
  private String osNetId;
  private String osSubNetId;
  private NetworkType osNetworkType;
  private String osProjectId;
  private String regionId;
  private Map<String, String> hostRoute = new HashMap<>();
}
