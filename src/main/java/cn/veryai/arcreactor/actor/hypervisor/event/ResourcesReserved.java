package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.Reservation;
import java.util.Objects;

public record ResourcesReserved(Reservation reservation) implements HypervisorEvent {
    public ResourcesReserved { Objects.requireNonNull(reservation); }
}
