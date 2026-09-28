package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

public final class InstanceWorkflowEntity {
    public static final String ENTITY_TYPE = "InstanceWorkflow";
    public static final EntityTypeKey<WorkflowCommand> TYPE_KEY =
            EntityTypeKey.create(WorkflowCommand.class, ENTITY_TYPE);

    private InstanceWorkflowEntity() {}
}
