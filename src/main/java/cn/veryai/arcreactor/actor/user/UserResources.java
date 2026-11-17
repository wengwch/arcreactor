package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.CleanUserResources;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.GetSystemShare;
import cn.veryai.arcreactor.actor.user.command.GetUserCleanup;
import cn.veryai.arcreactor.actor.user.command.CreateNetwork;
import cn.veryai.arcreactor.actor.user.command.GetNetwork;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.ShareKind;
import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

/**
 * Submits durable creation requests and queries their current state.
 */
@Service
public final class UserResources {
  private final PekkoRuntime runtime;

  public UserResources(PekkoRuntime runtime) {
    this.runtime = runtime;
  }

  /** Returns after persistence; UserActor assigns the share ID and returns it in the reply. */
  public CompletionStage<ShareReply> createShare(String userId, ShareSpec spec) {
    return AskPattern.<UserCommand, ShareReply>ask(
        runtime.userActor(userId),
        replyTo -> new CreateShare(spec, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  /** Ensures the user's single system share in the region. */
  public CompletionStage<ShareReply> createSystemShare(String userId, ShareSpec spec) {
    if (spec.kind() != ShareKind.SYSTEM) throw new IllegalArgumentException("A system share is required");
    return createShare(userId, spec);
  }

  public CompletionStage<ShareReply> getShare(String userId, String shareId) {
    return AskPattern.<UserCommand, ShareReply>ask(
        runtime.userActor(userId),
        replyTo -> new GetShare(shareId, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  public CompletionStage<ShareReply> getSystemShare(String userId, String regionId) {
    return AskPattern.<UserCommand, ShareReply>ask(
        runtime.userActor(userId),
        replyTo -> new GetSystemShare(regionId, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  /** Returns the persisted current state; conflicting specifications are rejected immediately. */
  public CompletionStage<ProjectReply> createProject(String userId, String clusterId, String name) {
    return AskPattern.<UserCommand, ProjectReply>ask(
        runtime.userActor(userId),
        replyTo -> new CreateProject(clusterId, name, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  public CompletionStage<ProjectReply> getProject(String userId, String clusterId) {
    return AskPattern.<UserCommand, ProjectReply>ask(
        runtime.userActor(userId),
        replyTo -> new GetProject(clusterId, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  /**
   * Requires a READY cluster project and exactly one preset shared/external LOCAL network.
   * Each user has one managed network per region. Returns after persisting the request;
   * use getNetwork to obtain the resource IDs once the state is READY.
   */
  public CompletionStage<NetworkReply> createNetwork(String userId, String clusterId, NetworkSpec spec) {
    return AskPattern.<UserCommand, NetworkReply>ask(
        runtime.userActor(userId),
        replyTo -> new CreateNetwork(clusterId, spec, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  public CompletionStage<NetworkReply> getNetwork(String userId, String regionId) {
    return AskPattern.<UserCommand, NetworkReply>ask(
        runtime.userActor(userId),
        replyTo -> new GetNetwork(regionId, replyTo),
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  /** Starts or resumes cleanup and returns after the request is accepted. */
  public CompletionStage<UserCleanupReply> cleanup(String userId) {
    return AskPattern.<UserCommand, UserCleanupReply>ask(
        runtime.userActor(userId),
        CleanUserResources::new,
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }

  public CompletionStage<UserCleanupReply> getCleanup(String userId) {
    return AskPattern.<UserCommand, UserCleanupReply>ask(
        runtime.userActor(userId),
        GetUserCleanup::new,
        runtime.getConfig().getAskTimeout(),
        runtime.getActorSystem().scheduler());
  }
}
