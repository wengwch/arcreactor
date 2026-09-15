package cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis;

public record HypervisorResourceRow(
        String hypervisorId,
        String status,
        String availabilityZone,
        String traitsJson,
        int totalVcpu,
        int reservedVcpu,
        int allocatedVcpu,
        long totalMemoryMb,
        long reservedMemoryMb,
        long allocatedMemoryMb,
        int totalGpu,
        int reservedGpu,
        int allocatedGpu,
        long resourceVersion) {
}
