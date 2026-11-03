package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Confirms that the share export and CephX access are usable. */
public record ShareBecameReady(String shareId, ShareSpec spec, String osShareId,
    String osExportLocationId, String exportLocationPath, String osGrantAccessId,
    String accessKey) implements UserEvent {
  @Override
  public String toString() {
    return "ShareBecameReady[shareId=" + shareId + ", osShareId=" + osShareId + ", accessKey=<redacted>]";
  }
}
