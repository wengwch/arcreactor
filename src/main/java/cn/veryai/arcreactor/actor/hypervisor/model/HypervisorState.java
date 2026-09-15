package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

public record HypervisorState(
        String hypervisorId,
        HypervisorStatus status,
        ResourceCapacity capacity,
        Map<String, Reservation> reservations,
        Map<String, Allocation> allocations,
        Map<String, ReservationOutcome> reservationOutcomes,
        long version) implements Serializable {

    public HypervisorState {
        if (hypervisorId == null || hypervisorId.isBlank()) throw new IllegalArgumentException("hypervisorId is required");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(capacity, "capacity");
        reservations = Map.copyOf(Objects.requireNonNull(reservations, "reservations"));
        allocations = Map.copyOf(Objects.requireNonNull(allocations, "allocations"));
        reservationOutcomes = Map.copyOf(Objects.requireNonNull(reservationOutcomes, "reservationOutcomes"));
        validate(capacity, reservations, allocations);
    }

    public static HypervisorState empty(String hypervisorId) {
        return new HypervisorState(hypervisorId, HypervisorStatus.ACTIVE, ResourceCapacity.empty(),
                Map.of(), Map.of(), Map.of(), 0);
    }

    public int reservedVcpus() { return reservations.values().stream().mapToInt(r -> r.resources().vcpus()).sum(); }
    public int allocatedVcpus() { return allocations.values().stream().mapToInt(a -> a.resources().vcpus()).sum(); }
    public long reservedMemoryMb() { return reservations.values().stream().mapToLong(r -> r.resources().memoryMb()).sum(); }
    public long allocatedMemoryMb() { return allocations.values().stream().mapToLong(a -> a.resources().memoryMb()).sum(); }
    public int reservedGpus() { return reservations.values().stream().mapToInt(r -> r.resources().gpuIds().size()).sum(); }
    public int allocatedGpus() { return allocations.values().stream().mapToInt(a -> a.resources().gpuIds().size()).sum(); }
    public int availableVcpus() { return capacity.totalVcpus() - reservedVcpus() - allocatedVcpus(); }
    public long availableMemoryMb() { return capacity.totalMemoryMb() - reservedMemoryMb() - allocatedMemoryMb(); }

    public List<Gpu> availableGpus() {
        Set<String> used = usedGpuIds(reservations, allocations);
        return capacity.gpus().stream().filter(g -> !used.contains(g.id())).toList();
    }

    public boolean canReserve(ResourceRequest request) {
        return assignResources(request).isPresent();
    }

    public Optional<ResourceRequest> assignResources(ResourceRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.vcpus() > availableVcpus() || request.memoryMb() > availableMemoryMb()) return Optional.empty();
        if (!request.gpuIds().isEmpty()) {
            Set<String> available = availableGpus().stream().map(Gpu::id).collect(java.util.stream.Collectors.toSet());
            return available.containsAll(request.gpuIds()) ? Optional.of(request) : Optional.empty();
        }
        List<String> matching = availableGpus().stream()
                .filter(g -> g.traits().containsAll(request.gpuRequest().requiredTraits()))
                .map(Gpu::id).sorted().limit(request.gpuRequest().count()).toList();
        if (matching.size() != request.gpuRequest().count()) return Optional.empty();
        return Optional.of(request.assignedTo(Set.copyOf(matching)));
    }

    public HypervisorState withReservation(Reservation reservation) {
        if (reservations.containsKey(reservation.reservationId()) || reservationOutcomes.containsKey(reservation.reservationId())) {
            throw new IllegalStateException("reservationId already used");
        }
        if (allocations.containsKey(reservation.instanceId())) throw new IllegalStateException("instance already allocated");
        Map<String, Reservation> next = new HashMap<>(reservations);
        next.put(reservation.reservationId(), reservation);
        return copy(status, capacity, next, allocations, reservationOutcomes);
    }

    public HypervisorState confirm(String reservationId, Instant allocatedAt) {
        Reservation reservation = Objects.requireNonNull(reservations.get(reservationId), "unknown reservation");
        Map<String, Reservation> nextReservations = new HashMap<>(reservations);
        nextReservations.remove(reservationId);
        Map<String, Allocation> nextAllocations = new HashMap<>(allocations);
        nextAllocations.put(reservation.instanceId(), new Allocation(reservation.instanceId(), reservationId,
                reservation.requestId(), reservation.resources(), allocatedAt));
        Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
        outcomes.put(reservationId, ReservationOutcome.CONFIRMED);
        return copy(status, capacity, nextReservations, nextAllocations, outcomes);
    }

    public HypervisorState removeReservation(String reservationId, ReservationOutcome outcome) {
        Map<String, Reservation> next = new HashMap<>(reservations);
        next.remove(reservationId);
        Map<String, ReservationOutcome> outcomes = new HashMap<>(reservationOutcomes);
        outcomes.put(reservationId, outcome);
        return copy(status, capacity, next, allocations, outcomes);
    }

    public HypervisorState removeAllocation(String instanceId) {
        Map<String, Allocation> next = new HashMap<>(allocations);
        next.remove(instanceId);
        return copy(status, capacity, reservations, next, reservationOutcomes);
    }

    public HypervisorState withStatus(HypervisorStatus nextStatus) {
        return copy(nextStatus, capacity, reservations, allocations, reservationOutcomes);
    }

    public HypervisorState withCapacity(ResourceCapacity nextCapacity) {
        return copy(status, nextCapacity, reservations, allocations, reservationOutcomes);
    }

    private HypervisorState copy(HypervisorStatus nextStatus, ResourceCapacity nextCapacity,
            Map<String, Reservation> nextReservations, Map<String, Allocation> nextAllocations,
            Map<String, ReservationOutcome> nextOutcomes) {
        return new HypervisorState(hypervisorId, nextStatus, nextCapacity, nextReservations, nextAllocations,
                nextOutcomes, version + 1);
    }

    private static void validate(ResourceCapacity capacity, Map<String, Reservation> reservations,
            Map<String, Allocation> allocations) {
        int cpu = reservations.values().stream().mapToInt(r -> r.resources().vcpus()).sum()
                + allocations.values().stream().mapToInt(a -> a.resources().vcpus()).sum();
        long memory = reservations.values().stream().mapToLong(r -> r.resources().memoryMb()).sum()
                + allocations.values().stream().mapToLong(a -> a.resources().memoryMb()).sum();
        if (cpu > capacity.totalVcpus()) throw new IllegalStateException("reserved + allocated CPU exceeds capacity");
        if (memory > capacity.totalMemoryMb()) throw new IllegalStateException("reserved + allocated memory exceeds capacity");
        Set<String> used = usedGpuIds(reservations, allocations);
        int gpuReferences = reservations.values().stream().mapToInt(r -> r.resources().gpuIds().size()).sum()
                + allocations.values().stream().mapToInt(a -> a.resources().gpuIds().size()).sum();
        if (used.size() != gpuReferences) throw new IllegalStateException("GPU assigned more than once");
        Set<String> capacityIds = new HashSet<>();
        capacity.gpus().forEach(g -> capacityIds.add(g.id()));
        if (!capacityIds.containsAll(used)) throw new IllegalStateException("assigned GPU is outside capacity");
        List<String> instanceIds = new ArrayList<>(allocations.keySet());
        if (new HashSet<>(instanceIds).size() != instanceIds.size()) throw new IllegalStateException("duplicate allocation");
    }

    private static Set<String> usedGpuIds(Map<String, Reservation> reservations, Map<String, Allocation> allocations) {
        Set<String> used = new HashSet<>();
        reservations.values().forEach(r -> used.addAll(r.resources().gpuIds()));
        allocations.values().forEach(a -> used.addAll(a.resources().gpuIds()));
        return used;
    }
}
