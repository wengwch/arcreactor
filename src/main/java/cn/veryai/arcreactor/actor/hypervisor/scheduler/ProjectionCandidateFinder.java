package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.model.HypervisorStatus;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import cn.veryai.arcreactor.repo.HypervisorReadRepo;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorResourceRow;
import cn.veryai.arcreactor.util.JsonUtil;
import java.util.List;
import java.util.Set;
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
                        request.resources().memoryMb(),
                        request.constraints().availabilityZone(),
                        encode(request.constraints().requiredTraits()),
                        request.resources().gpuRequest(),
                        queryLimit).stream()
                .map(this::mapCandidate)
                .toList(), blockingExecutor);
    }

    private HypervisorCandidate mapCandidate(HypervisorResourceRow row) {
        return new HypervisorCandidate(row.hypervisorId(), HypervisorStatus.valueOf(row.status()),
                row.availabilityZone(), decode(row.traitsJson()),
                row.totalVcpu(), row.reservedVcpu(), row.allocatedVcpu(),
                row.totalMemoryMb(), row.reservedMemoryMb(), row.allocatedMemoryMb(),
                row.totalGpu(), row.reservedGpu(), row.allocatedGpu(),
                row.resourceVersion());
    }

    private static String encode(Set<String> values) {
        return JsonUtil.toJson(values.stream().sorted().toList());
    }

    private static Set<String> decode(String json) {
        List<String> values = JsonUtil.toList(json, String.class);
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
