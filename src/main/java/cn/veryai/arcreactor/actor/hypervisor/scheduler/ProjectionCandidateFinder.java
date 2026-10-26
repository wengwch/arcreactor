package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.enums.HypervisorStatus;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import cn.veryai.arcreactor.repo.HypervisorReadRepo;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorResourceRow;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** MyBatis-backed query side of the scheduler. */
public final class ProjectionCandidateFinder implements CandidateFinder {
    private final HypervisorReadRepo repo;
    private final Executor blockingExecutor;
    private final int queryLimit;

    public ProjectionCandidateFinder(HypervisorReadRepo repo, Executor blockingExecutor, int queryLimit) {
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
                        request.constraints().availabilityZone(),
                        request.resources().gpuRequest(),
                        request.constraints().regionId(),
                        request.constraints().hypervisorType(),
                        queryLimit).stream()
                .map(this::mapCandidate)
                .toList(), blockingExecutor);
    }

    private HypervisorCandidate mapCandidate(HypervisorResourceRow row) {
        return new HypervisorCandidate(row.hypervisorId(), HypervisorStatus.valueOf(row.status()),
                row.availabilityZone(),
                row.totalVcpu(), row.reservedVcpu(), row.allocatedVcpu(),
                row.totalMemoryGb(), row.reservedMemoryGb(), row.allocatedMemoryGb(),
                row.totalGpu(), row.reservedGpu(), row.allocatedGpu(),
                row.resourceVersion());
    }
}
