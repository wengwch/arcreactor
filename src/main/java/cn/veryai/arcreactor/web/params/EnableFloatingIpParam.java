package cn.veryai.arcreactor.web.params;

import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class EnableFloatingIpParam {
    /** Omitted preserves imported usage; an empty list clears it while retaining claim reservations. */
    @Valid
    private List<@NotNull PortForwardingSnapshot> forwardings;
}
