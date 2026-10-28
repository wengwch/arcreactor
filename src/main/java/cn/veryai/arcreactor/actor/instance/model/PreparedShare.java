package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.UserShareState;
import java.io.Serializable;

/** Captured only after the shared resource and its access rule are ready. */
public record PreparedShare(String osShareId, String osExportLocationId, String exportLocationPath,
    String osGrantAccessId, String accessKey) implements Serializable {
  public PreparedShare {
    for (String value : new String[] {osShareId, osExportLocationId, exportLocationPath, osGrantAccessId, accessKey}) {
      if (value == null || value.isBlank()) throw new IllegalArgumentException("Incomplete prepared share");
    }
  }

  public static PreparedShare from(UserShareState share) {
    if (share == null || share.phase() != UserShareState.Phase.READY) {
      throw new IllegalArgumentException("Share must be READY");
    }
    return new PreparedShare(share.osShareId(), share.osExportLocationId(), share.exportLocationPath(),
        share.osGrantAccessId(), share.accessKey());
  }

  @Override
  public String toString() {
    return "PreparedShare[osShareId=" + osShareId + ", osExportLocationId=" + osExportLocationId
        + ", osGrantAccessId=" + osGrantAccessId + "]";
  }
}
