package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** Reads projected host resources through the shared host repository. */
public final class ProjectionCandidateFinder implements CandidateFinder {
    private final HypervisorHostRepo repo;
    private final Executor blockingExecutor;
    private final int queryLimit;

    public ProjectionCandidateFinder(HypervisorHostRepo repo, Executor blockingExecutor, int queryLimit) {
        if (queryLimit <= 0) throw new IllegalArgumentException("queryLimit must be positive");
        this.repo = repo;
        this.blockingExecutor = blockingExecutor;
        this.queryLimit = queryLimit;
    }

    @Override
    public CompletionStage<List<HypervisorCandidate>> find(SchedulingRequest request) {
        return CompletableFuture.supplyAsync(() -> repo.findCandidates(
                        request.resources().vcpus(),
                        request.resources().memoryGb(),
                        request.resources().diskGb(),
                        request.constraints().availabilityZone(),
                        request.resources().gpuRequest(),
                        request.constraints().regionId(),
                        request.constraints().hypervisorType(),
                        request.constraints().hostname(),
                        queryLimit).stream()
                .map(this::mapCandidate)
                .toList(), blockingExecutor);
    }

    private HypervisorCandidate mapCandidate(HypervisorHostEntity host) {
        return new HypervisorCandidate(host.getId(), host.getHypervisorStatus(),
                host.getAvailabilityZone(), host.getHostname(),
                host.getVcpus(), host.getReservedVcpu(), host.getUsedVcpus(),
                host.getRam(), host.getReservedMemoryGb(), host.getUsedRam(),
                host.getDisk(), host.getReservedDiskGb(), host.getUsedDisk(),
                host.getGpus(), host.getReservedGpu(), host.getUsedGpus(),
                host.getResourceVersion());
    }
}
