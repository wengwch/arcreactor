package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.io.Serializable;

public interface FloatingIpEvent extends Serializable {
  FloatingIpIdentity identity();
}
