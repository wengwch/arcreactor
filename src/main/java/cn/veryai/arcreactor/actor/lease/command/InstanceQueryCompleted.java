package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;

public record InstanceQueryCompleted(String pollId, InstanceWorkflowState instance, String error) implements LeaseCommand {}
