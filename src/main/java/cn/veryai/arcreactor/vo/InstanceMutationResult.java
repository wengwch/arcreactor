package cn.veryai.arcreactor.vo;

/** An accepted asynchronous mutation, or an immediate no-op success when operationId is null. */
public record InstanceMutationResult(String operationId) {}
