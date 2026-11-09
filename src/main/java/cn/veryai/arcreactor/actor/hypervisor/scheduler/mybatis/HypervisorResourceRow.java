package cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis;

public record HypervisorResourceRow(
        String hypervisorId,
        String status,
        String availabilityZone,
        String hostname,
        int totalVcpu,
        int reservedVcpu,
        int usedVcpu,
        long totalMemoryGb,
        long reservedMemoryGb,
        long usedMemoryGb,
        long totalDiskGb,
        long reservedDiskGb,
        long usedDiskGb,
        int totalGpu,
        int reservedGpu,
        int usedGpu,
        long resourceVersion) {
}
