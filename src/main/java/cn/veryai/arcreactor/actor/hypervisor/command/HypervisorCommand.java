package cn.veryai.arcreactor.actor.hypervisor.command;

import java.io.Serializable;

public sealed interface HypervisorCommand extends Serializable permits ReserveResources, ConfirmReservation,
        CancelReservation, ReleaseResources, EnableHypervisor, DrainHypervisor,
        EnterMaintenance, UpdateCapacity, GetHypervisorState, CleanReservationOutcomes {}
