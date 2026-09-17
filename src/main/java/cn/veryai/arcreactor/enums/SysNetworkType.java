package cn.veryai.arcreactor.enums;

import lombok.Getter;
import org.openstack4j.model.network.NetworkType;

public enum SysNetworkType {
  PUBLIC(NetworkType.VLAN),
  LOCAL(NetworkType.VLAN),
  SHARED(NetworkType.GENEVE),
  ;
  @Getter
  private final NetworkType networkType;

  SysNetworkType(NetworkType networkType) {
    this.networkType = networkType;
  }
}
