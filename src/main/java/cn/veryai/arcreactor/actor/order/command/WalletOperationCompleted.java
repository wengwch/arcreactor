package cn.veryai.arcreactor.actor.order.command;

/** success=null retries the operation; false means insufficient funds. */
public record WalletOperationCompleted(String token, Boolean success, String error) implements OrderCommand {}
