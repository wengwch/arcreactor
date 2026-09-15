package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.AvailabilityZoneFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.CapacityFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.GpuFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.TraitFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.mybatis.HypervisorReadMapper;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.CpuBalanceScorer;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.GpuFragmentationScorer;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.MemoryBalanceScorer;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.DispatcherSelector;

import java.time.Duration;
import java.util.List;

public final class SchedulerFactory {
    private SchedulerFactory() {}

    public static SchedulerService create(ActorSystem<?> system, HypervisorReadMapper readMapper) {
        var config = system.settings().config().getConfig("cloud.scheduler");
        var blockingExecutor = system.dispatchers().lookup(
                DispatcherSelector.fromConfig("virtual-thread-dispatcher"));
        CandidateFinder finder = new ProjectionCandidateFinder(
                readMapper, blockingExecutor, config.getInt("candidate-query-limit"));
        AllocationClaimer claimer = new PekkoAllocationClaimer(system,
                Duration.ofMillis(config.getDuration("ask-timeout").toMillis()));
        return new SchedulerService(finder,
                List.of(new CapacityFilter(), new AvailabilityZoneFilter(), new TraitFilter(), new GpuFilter()),
                List.of(new SchedulerService.WeightedScorer(new CpuBalanceScorer(), 0.4),
                        new SchedulerService.WeightedScorer(new MemoryBalanceScorer(), 0.4),
                        new SchedulerService.WeightedScorer(new GpuFragmentationScorer(), 0.2)),
                claimer, config.getInt("max-claim-attempts"), SchedulerMetrics.NOOP);
    }
}
