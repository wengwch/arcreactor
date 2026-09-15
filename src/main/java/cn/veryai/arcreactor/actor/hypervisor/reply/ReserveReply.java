package cn.veryai.arcreactor.actor.hypervisor.reply;

import java.io.Serializable;

public sealed interface ReserveReply extends Serializable permits ReserveAccepted, ReserveRejected {}
