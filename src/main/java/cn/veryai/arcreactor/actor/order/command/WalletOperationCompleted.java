package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.WalletAction;

public record WalletOperationCompleted(String token, WalletAction action, Boolean success,
    String error) implements OrderCommand {}
