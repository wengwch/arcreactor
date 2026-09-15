package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;

public enum ReservationOutcome implements Serializable {
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
