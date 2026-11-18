package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record InstanceForwardingsDeleted(String instanceId, Instant completedAt) implements InstanceWorkflowEvent {
  public static List<InstanceWorkflowEvent> completionEvents(InstanceWorkflowState state, Instant completedAt) {
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    for (InstanceForwardingSpec spec : state.currentUserForwardings()) {
      InstanceForwardingSpec deleted = spec.transition(InstanceForwardingSpec.Phase.DELETED, null);
      events.add(new UserForwardingDeleted(state.instanceId(), deleted));
    }
    events.add(new InstanceForwardingsDeleted(state.instanceId(), completedAt));
    return List.copyOf(events);
  }
}
