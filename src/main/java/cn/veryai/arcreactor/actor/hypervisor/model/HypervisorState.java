package cn.veryai.arcreactor.actor.hypervisor.model;

import cn.veryai.arcreactor.enums.HypervisorStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

public record HypervisorState(
    String hypervisorId,
    HypervisorStatus status,
    ResourceCapacity capacity,
    ResourceUsage baselineUsage,
    Map<String, Reservation> reservations,
    Map<String, Allocation> allocations,
    Map<String, ReservationOutcome> reservationOutcomes,
    long version,
    Map<String, Instant> reservationOutcomeTimes,
    int runningVms)
    implements Serializable {

  public HypervisorState {
    if (hypervisorId == null || hypervisorId.isBlank())
      throw new IllegalArgumentException("hypervisorId is required");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(capacity, "capacity");
    Objects.requireNonNull(baselineUsage, "baselineUsage");
    reservations = Map.copyOf(Objects.requireNonNull(reservations, "reservations"));
    allocations = Map.copyOf(Objects.requireNonNull(allocations, "allocations"));
    if (runningVms != allocations.size()) {
      throw new IllegalArgumentException("runningVms must match the number of allocations");
    }
    reservationOutcomes =
        Map.copyOf(Objects.requireNonNull(reservationOutcomes, "reservationOutcomes"));
    reservationOutcomeTimes =
        Map.copyOf(Objects.requireNonNullElse(reservationOutcomeTimes, Map.of()));
    validate(capacity, baselineUsage, reservations, allocations);
  }

  public static HypervisorState empty(String hypervisorId) {
    return new HypervisorState(
        hypervisorId,
        HypervisorStatus.MAINTENANCE,
        ResourceCapacity.empty(),
        ResourceUsage.empty(),
        Map.of(),
        Map.of(),
        Map.of(),
        0,
        Map.of(),
        0);
  }

  public int reservedVcpus() {
    return reservations.values().stream().mapToInt(r -> r.resources().vcpus()).sum();
  }

  public int usedVcpus() {
    return baselineUsage.vcpus() + allocations.values().stream().mapToInt(a -> a.resources().vcpus()).sum();
  }

  public long reservedMemoryGb() {
    return reservations.values().stream().mapToLong(r -> r.resources().memoryGb()).sum();
  }

  public long usedMemoryGb() {
    return baselineUsage.memoryGb() + allocations.values().stream().mapToLong(a -> a.resources().memoryGb()).sum();
  }

  public long reservedDiskGb() {
    return reservations.values().stream().mapToLong(r -> r.resources().diskGb()).sum();
  }

  public long usedDiskGb() {
    return baselineUsage.diskGb() + allocations.values().stream().mapToLong(a -> a.resources().diskGb()).sum();
  }

  public int reservedGpus() {
    return reservations.values().stream().mapToInt(r -> r.resources().gpuRequest()).sum();
  }

  public int usedGpus() {
    return baselineUsage.gpus() + allocations.values().stream().mapToInt(a -> a.resources().gpuRequest()).sum();
  }

  public int availableVcpus() {
    return capacity.totalVcpus() - reservedVcpus() - usedVcpus();
  }

  public long availableMemoryGb() {
    return capacity.totalMemoryGb() - reservedMemoryGb() - usedMemoryGb();
  }

  public long availableDiskGb() {
    return capacity.totalDiskGb() - reservedDiskGb() - usedDiskGb();
  }

  public int availableGpus() {
    return capacity.gpus() - reservedGpus() - usedGpus();
  }

  public boolean canReserve(ResourceRequest request) {
    return assignResources(request).isPresent();
  }

  public Optional<ResourceRequest> assignResources(ResourceRequest request) {
    Objects.requireNonNull(request, "request");
    if (request.vcpus() > availableVcpus()
        || request.memoryGb() > availableMemoryGb()
        || request.diskGb() > availableDiskGb()
        || request.gpuRequest() > availableGpus()) return Optional.empty();
    return Optional.of(request);
  }

  public HypervisorState withReservation(Reservation reservation) {
    if (reservations.containsKey(reservation.reservationId())
        || reservationOutcomes.containsKey(reservation.reservationId())) {
      throw new IllegalStateException("reservationId already used");
    }
    if (allocations.containsKey(reservation.instanceId()))
      throw new IllegalStateException("instance already allocated");
    Map<String, Reservation> next = new HashMap<>(reservations);
    next.put(reservation.reservationId(), reservation);
    return copy(status, capacity, next, allocations, reservationOutcomes);
  }

  public HypervisorState confirm(String reservationId, Instant allocatedAt) {
    Reservation reservation =
        Objects.requireNonNull(reservations.get(reservationId), "unknown reservation");
    Map<String, Reservation> nextReservations = new HashMap<>(reservations);
    nextReservations.remove(reservationId);
    Map<String, Allocation> nextAllocations = new HashMap<>(allocations);
    nextAllocations.put(
        reservation.instanceId(),
        new Allocation(
            reservation.instanceId(),
            reservationId,
            reservation.requestId(),
            reservation.resources(),
            allocatedAt));
    Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
    outcomes.put(reservationId, ReservationOutcome.CONFIRMED);
    Map<String, Instant> times = new HashMap<>(reservationOutcomeTimes);
    times.put(reservationId, allocatedAt);
    return copy(status, capacity, nextReservations, nextAllocations, outcomes, times);
  }

  public HypervisorState removeReservation(
      String reservationId, ReservationOutcome outcome, Instant finalizedAt) {
    Map<String, Reservation> next = new HashMap<>(reservations);
    next.remove(reservationId);
    Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
    outcomes.put(reservationId, outcome);
    Map<String, Instant> times = new HashMap<>(reservationOutcomeTimes);
    times.put(reservationId, finalizedAt);
    return copy(status, capacity, next, allocations, outcomes, times);
  }

  public HypervisorState rejectReservation(String reservationId, Instant rejectedAt) {
    if (reservations.containsKey(reservationId) || reservationOutcomes.containsKey(reservationId)
        || allocations.values().stream().anyMatch(allocation -> reservationId.equals(allocation.reservationId()))) {
      throw new IllegalStateException("reservation already exists or was finalized");
    }
    Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
    outcomes.put(reservationId, ReservationOutcome.REJECTED);
    Map<String, Instant> times = new HashMap<>(reservationOutcomeTimes);
    times.put(reservationId, rejectedAt);
    return copy(status, capacity, reservations, allocations, outcomes, times);
  }

  public HypervisorState removeAllocation(String instanceId, Instant releasedAt) {
    Map<String, Allocation> next = new HashMap<>(allocations);
    Allocation removed = next.remove(instanceId);
    Map<String, Instant> times = new HashMap<>(reservationOutcomeTimes);
    if (removed != null && reservationOutcomes.containsKey(removed.reservationId())) {
      times.put(removed.reservationId(), releasedAt);
    }
    return copy(status, capacity, reservations, next, reservationOutcomes, times);
  }

  public HypervisorState withStatus(HypervisorStatus nextStatus) {
    return copy(nextStatus, capacity, reservations, allocations, reservationOutcomes);
  }

  public boolean canUpdateCapacity() {
    return status == HypervisorStatus.MAINTENANCE && reservations.isEmpty() && allocations.isEmpty();
  }

  public HypervisorState withCapacity(ResourceCapacity nextCapacity, ResourceUsage nextUsage) {
    if (!canUpdateCapacity()) {
      throw new IllegalStateException("capacity updates require maintenance with no reservations or allocations");
    }
    return new HypervisorState(hypervisorId, status, nextCapacity, nextUsage, reservations, allocations,
        reservationOutcomes, version + 1, reservationOutcomeTimes, runningVms);
  }

  private HypervisorState copy(
      HypervisorStatus nextStatus,
      ResourceCapacity nextCapacity,
      Map<String, Reservation> nextReservations,
      Map<String, Allocation> nextAllocations,
      Map<String, ReservationOutcome> nextOutcomes) {
    return copy(
        nextStatus,
        nextCapacity,
        nextReservations,
        nextAllocations,
        nextOutcomes,
        reservationOutcomeTimes);
  }

  private HypervisorState copy(
      HypervisorStatus nextStatus,
      ResourceCapacity nextCapacity,
      Map<String, Reservation> nextReservations,
      Map<String, Allocation> nextAllocations,
      Map<String, ReservationOutcome> nextOutcomes,
      Map<String, Instant> nextOutcomeTimes) {
    return new HypervisorState(
        hypervisorId,
        nextStatus,
        nextCapacity,
        baselineUsage,
        nextReservations,
        nextAllocations,
        nextOutcomes,
        version + 1,
        nextOutcomeTimes,
        nextAllocations.size());
  }

  public Set<String> expiredReservationOutcomes(Instant cutoff) {
    Set<String> active = new HashSet<>();
    allocations.values().forEach(allocation -> active.add(allocation.reservationId()));
    Set<String> expired = new HashSet<>();
    reservationOutcomes
        .keySet()
        .forEach(
            id -> {
              Instant finalizedAt = reservationOutcomeTimes.get(id);
              if (!active.contains(id)
                  && !reservations.containsKey(id)
                  && finalizedAt != null
                  && !finalizedAt.isAfter(cutoff)) {
                expired.add(id);
              }
            });
    return Set.copyOf(expired);
  }

  public HypervisorState cleanReservationOutcomes(Set<String> expiredIds, Instant cleanedAt) {
    Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
    Map<String, Instant> times = new HashMap<>(reservationOutcomeTimes);
    expiredIds.forEach(
        id -> {
          outcomes.remove(id);
          times.remove(id);
        });
    // Older snapshots have no timestamps: start their retention window on the first cleanup.
    outcomes.keySet().forEach(id -> times.putIfAbsent(id, cleanedAt));
    return copy(status, capacity, reservations, allocations, outcomes, times);
  }

  private static void validate(
      ResourceCapacity capacity,
      ResourceUsage baselineUsage,
      Map<String, Reservation> reservations,
      Map<String, Allocation> allocations) {
    long cpu = baselineUsage.vcpus()
        + reservations.values().stream().mapToLong(r -> r.resources().vcpus()).sum()
            + allocations.values().stream().mapToLong(a -> a.resources().vcpus()).sum();
    long memory =
        baselineUsage.memoryGb() + reservations.values().stream().mapToLong(r -> r.resources().memoryGb()).sum()
            + allocations.values().stream().mapToLong(a -> a.resources().memoryGb()).sum();
    if (cpu > capacity.totalVcpus())
      throw new IllegalStateException("reserved + used CPU exceeds capacity");
    if (memory > capacity.totalMemoryGb())
      throw new IllegalStateException("reserved + used memory exceeds capacity");
    long disk =
        baselineUsage.diskGb() + reservations.values().stream().mapToLong(r -> r.resources().diskGb()).sum()
            + allocations.values().stream().mapToLong(a -> a.resources().diskGb()).sum();
    if (disk > capacity.totalDiskGb())
      throw new IllegalStateException("reserved + used disk exceeds capacity");
    long gpus = baselineUsage.gpus()
        + reservations.values().stream().mapToLong(r -> r.resources().gpuRequest()).sum()
            + allocations.values().stream().mapToLong(a -> a.resources().gpuRequest()).sum();
    if (gpus > capacity.gpus())
      throw new IllegalStateException("reserved + used GPU exceeds capacity");
    List<String> instanceIds = new ArrayList<>(allocations.keySet());
    if (new HashSet<>(instanceIds).size() != instanceIds.size())
      throw new IllegalStateException("duplicate allocation");
  }
}
