package cn.veryai.arcreactor.actor.instance.event;

/** The previous candidate has failed and completed cleanup before advancing. */
public record ForwardingCandidateSelected(String instanceId, int candidateIndex) implements InstanceWorkflowEvent {}
