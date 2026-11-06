package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class EnableFloatingIpParam {
    /** Rules add occupied ports to current usage; omitted, null and empty lists preserve usage. */
    @Valid
    private List<@NotNull PortForwardingSnapshot> forwardings;
}
