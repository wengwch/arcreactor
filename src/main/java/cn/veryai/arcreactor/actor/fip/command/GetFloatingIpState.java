package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpState;
import org.apache.pekko.actor.typed.ActorRef;

public record GetFloatingIpState(ActorRef<FloatingIpState> replyTo) implements FloatingIpCommand {}
