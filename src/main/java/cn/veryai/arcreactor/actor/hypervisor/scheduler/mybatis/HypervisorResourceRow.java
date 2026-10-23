package cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis;

public record HypervisorResourceRow(
        String hypervisorId,
        String status,
        String availabilityZone,
        int totalVcpu,
        int reservedVcpu,
        int allocatedVcpu,
        long totalMemoryGb,
        long reservedMemoryGb,
        long allocatedMemoryGb,
        int totalGpu,
        int reservedGpu,
        int allocatedGpu,
        long resourceVersion) {
}
