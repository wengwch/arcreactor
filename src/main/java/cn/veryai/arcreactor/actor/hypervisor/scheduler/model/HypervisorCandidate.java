package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.io.Serializable;
import java.util.Objects;

public record HypervisorCandidate(String hypervisorId, HypervisorStatus status, String availabilityZone, String hostname,
        int totalVcpus, int reservedVcpus, int usedVcpus,
        long totalMemoryGb, long reservedMemoryGb, long usedMemoryGb,
        long totalDiskGb, long reservedDiskGb, long usedDiskGb,
        int totalGpus, int reservedGpus, int usedGpus,
        long resourceVersion) implements Serializable {
    public HypervisorCandidate {
        Objects.requireNonNull(hypervisorId);
        Objects.requireNonNull(status);
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }
    public int availableVcpus() { return totalVcpus - reservedVcpus - usedVcpus; }
    public long availableMemoryGb() { return totalMemoryGb - reservedMemoryGb - usedMemoryGb; }
    public long availableDiskGb() { return totalDiskGb - reservedDiskGb - usedDiskGb; }
    public int availableGpuCount() { return totalGpus - reservedGpus - usedGpus; }
}
