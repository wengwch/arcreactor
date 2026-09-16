package cn.veryai.arcreactor.actor.hypervisor.event;

import java.io.Serializable;

public sealed interface HypervisorEvent extends Serializable permits ResourcesReserved, ReservationConfirmed,
        ReservationCancelled, ReservationExpired, ResourcesReleased, CapacityUpdated, HypervisorEnabled,
        HypervisorDrainStarted, HypervisorMaintenanceEntered, ReservationOutcomesCleaned {}
