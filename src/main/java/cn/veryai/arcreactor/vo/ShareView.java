package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.user.model.ShareKind;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import java.io.Serializable;
import org.openstack4j.model.manila.Share;

/** Public share status, without Manila access credentials. */
public record ShareView(
        String shareId,
        String userId,
        String regionId,
        ShareKind kind,
        String phase,
        int size,
        Share.Protocol shareProto,
        String shareType,
        String osShareId,
        String exportLocationPath,
        String failureMessage) implements Serializable {

    public static ShareView from(String userId, UserShareState share) {
        return new ShareView(share.shareId(), userId, share.regionId(), share.spec().kind(),
                share.phase().name(), share.spec().size(), share.spec().shareProto(), share.spec().shareType(),
                share.osShareId(), share.exportLocationPath(), share.failureMessage());
    }
}
