package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import cn.veryai.arcreactor.actor.hypervisor.model.HypervisorStatus;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

public record HypervisorCandidate(String hypervisorId, HypervisorStatus status, String availabilityZone,
        Set<String> traits, int totalVcpus, int reservedVcpus, int allocatedVcpus,
        long totalMemoryMb, long reservedMemoryMb, long allocatedMemoryMb,
        int totalGpus, int reservedGpus, int allocatedGpus,
        long resourceVersion) implements Serializable {
    public HypervisorCandidate {
        Objects.requireNonNull(hypervisorId);
        Objects.requireNonNull(status);
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
        traits = Set.copyOf(Objects.requireNonNullElse(traits, Set.of()));
    }
    public int availableVcpus() { return totalVcpus - reservedVcpus - allocatedVcpus; }
    public long availableMemoryMb() { return totalMemoryMb - reservedMemoryMb - allocatedMemoryMb; }
    public int availableGpuCount() { return totalGpus - reservedGpus - allocatedGpus; }
}
