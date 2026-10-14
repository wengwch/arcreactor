package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.UserShareState;
import java.io.Serializable;

/** Captured only after the shared resource and its access rule are ready. */
public record PreparedShare(String osShareId, String osExportLocationId, String exportLocationPath,
    String osGrantAccessId, String accessKey) implements Serializable {
  public static PreparedShare from(UserShareState share) {
    return new PreparedShare(share.osShareId(), share.osExportLocationId(), share.exportLocationPath(),
        share.osGrantAccessId(), share.accessKey());
  }

  @Override
  public String toString() {
    return "PreparedShare[osShareId=" + osShareId + ", osExportLocationId=" + osExportLocationId
        + ", osGrantAccessId=" + osGrantAccessId + "]";
  }
}
