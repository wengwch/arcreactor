package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

/** Routes each request to one IP actor. Candidate selection belongs to the instance workflow. */
@Service
public final class FloatingIpForwardings {
  private final PekkoRuntime runtime;

  public FloatingIpForwardings(PekkoRuntime runtime) {
    this.runtime = runtime;
  }

  /** Rules are merged into current usage only while disabled; null and empty lists preserve usage. */
  public CompletionStage<Boolean> enable(FloatingIpIdentity identity, List<PortForwardingSnapshot> forwardings) {
    Objects.requireNonNull(identity, "identity");
    return AskPattern.<FloatingIpCommand, Boolean>ask(runtime.floatingIpActor(identity.floatingIpId()),
        replyTo -> new EnableFloatingIp(identity, forwardings, replyTo), runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  public CompletionStage<Boolean> disable(String floatingIpId) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(runtime.floatingIpActor(floatingIpId),
        DisableFloatingIp::new, runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  public CompletionStage<ForwardingReply> create(String floatingIpId, String claimId, ForwardingSpec spec) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(runtime.floatingIpActor(floatingIpId),
        replyTo -> new CreatePortForwarding(claimId, spec, replyTo), runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  public CompletionStage<ForwardingReply> get(String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(runtime.floatingIpActor(floatingIpId),
        replyTo -> new GetPortForwarding(claimId, replyTo), runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  /** Deletes by claim ID. CREATING mappings reject deletion; poll until the reply is DELETED. */
  public CompletionStage<ForwardingReply> delete(String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(runtime.floatingIpActor(floatingIpId),
        replyTo -> new DeletePortForwarding(claimId, replyTo), runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }
}
