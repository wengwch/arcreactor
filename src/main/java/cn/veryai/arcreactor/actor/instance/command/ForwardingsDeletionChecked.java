package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import java.io.Serializable;
import java.util.List;

public record ForwardingsDeletionChecked(long generation, String requestToken, List<Result> results, String failure)
        implements WorkflowCommand {
    public ForwardingsDeletionChecked {
        results = List.copyOf(results);
    }

    public record Result(PreparedForwarding forwarding, ForwardingReply reply, String failure)
            implements Serializable {
    }
}
