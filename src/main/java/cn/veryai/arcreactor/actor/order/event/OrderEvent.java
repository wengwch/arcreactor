package cn.veryai.arcreactor.actor.order.event;

import java.io.Serializable;

public sealed interface OrderEvent extends Serializable permits OrderCreated,
    OrderLocked, OrderDeductionRequested, OrderDeducted, OrderInsufficientFunds, OrderOperationFailed {}
