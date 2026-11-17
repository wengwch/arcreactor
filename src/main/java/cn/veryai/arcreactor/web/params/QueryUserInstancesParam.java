package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QueryUserInstancesParam {
    @Size(max = 255)
    private String regionId;

    private InstancePhase phase;
}
