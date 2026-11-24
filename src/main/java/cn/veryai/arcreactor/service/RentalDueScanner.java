package cn.veryai.arcreactor.service;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.lease.command.ReconcileLease;
import cn.veryai.arcreactor.entity.LeaseDueEntry;
import cn.veryai.arcreactor.repo.RentalViewRepo;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Checks persisted lease deadlines; order polling belongs to the actors themselves. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentalDueScanner {
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES_PER_TICK = 50;

    private final PekkoRuntime runtime;
    private final RentalViewRepo repository;
    private ScheduledExecutorService scheduler;
    private Instant cutoff;
    private LeaseDueEntry cursor;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (scheduler != null || !runtime.getConfig().isProjectionEnabled()) return;
        long intervalMillis = runtime.getConfig().getRentalScanInterval().toMillis();
        if (intervalMillis <= 0) throw new IllegalArgumentException("rental scan interval must be positive");
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("rental-due-scanner").factory());
        scheduler.scheduleWithFixedDelay(this::scan, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void scan() {
        try {
            if (cutoff == null) cutoff = Instant.now();
            for (int page = 0; page < MAX_PAGES_PER_TICK; page++) {
                var entries = repository.findDueLeases(cutoff, cursor, PAGE_SIZE);
                for (LeaseDueEntry entry : entries) {
                    runtime.leaseActor(entry.leaseId()).tell(new ReconcileLease());
                }
                if (entries.size() < PAGE_SIZE) {
                    cursor = null;
                    cutoff = null;
                    return;
                }
                cursor = entries.getLast();
            }
            // Keep this scan's cursor across ticks so repeatedly due first pages cannot starve later rows.
        } catch (RuntimeException error) {
            log.warn("Rental due scan failed; the next tick will retry", error);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
