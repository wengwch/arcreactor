package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.List;
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

  /** Ports are merged only while disabled; already enabled IPs return success without changing usage. */
  public CompletionStage<Boolean> enable(FloatingIpIdentity identity, List<Integer> externalPorts) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(runtime.floatingIpActor(identity.floatingIpId()),
        replyTo -> new EnableFloatingIp(identity, externalPorts, replyTo), runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  public CompletionStage<Boolean> disable(String floatingIpId) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(runtime.floatingIpActor(floatingIpId),
        DisableFloatingIp::new, runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  /** Acknowledges acceptance, requiring a disabled actor without occupied ports; query state for completion. */
  public CompletionStage<Boolean> destroy(String floatingIpId) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(runtime.floatingIpActor(floatingIpId),
        DestroyFloatingIp::new, runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
  }

  public CompletionStage<FloatingIpState> getState(String floatingIpId) {
    return AskPattern.<FloatingIpCommand, FloatingIpState>ask(runtime.floatingIpActor(floatingIpId),
        GetFloatingIpState::new, runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
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
