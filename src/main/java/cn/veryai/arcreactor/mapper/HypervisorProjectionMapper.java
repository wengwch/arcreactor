package cn.veryai.arcreactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface HypervisorProjectionMapper {
    int upsertCapacity(
            @Param("hypervisorId") String hypervisorId,
            @Param("availabilityZone") String availabilityZone,
            @Param("totalVcpu") int totalVcpu,
            @Param("totalMemoryMb") long totalMemoryMb,
            @Param("totalGpu") int totalGpu,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int deleteAllGpus(@Param("hypervisorId") String hypervisorId);

    int deleteGpusExcept(
            @Param("hypervisorId") String hypervisorId,
            @Param("gpuIds") List<String> gpuIds);

    int upsertGpu(
            @Param("hypervisorId") String hypervisorId,
            @Param("gpuId") String gpuId,
            @Param("model") String model,
            @Param("traits") String traits,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int applyResourceDelta(
            @Param("hypervisorId") String hypervisorId,
            @Param("reservedVcpuDelta") int reservedVcpuDelta,
            @Param("allocatedVcpuDelta") int allocatedVcpuDelta,
            @Param("reservedMemoryMbDelta") long reservedMemoryMbDelta,
            @Param("allocatedMemoryMbDelta") long allocatedMemoryMbDelta,
            @Param("reservedGpuDelta") int reservedGpuDelta,
            @Param("allocatedGpuDelta") int allocatedGpuDelta,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int applyStatus(
            @Param("hypervisorId") String hypervisorId,
            @Param("status") String status,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int upsertAllocation(
            @Param("instanceId") String instanceId,
            @Param("hypervisorId") String hypervisorId,
            @Param("reservationId") String reservationId,
            @Param("vcpus") int vcpus,
            @Param("memoryMb") long memoryMb,
            @Param("gpuCount") int gpuCount,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int markAllocationReleased(
            @Param("instanceId") String instanceId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);

    int updateGpu(
            @Param("hypervisorId") String hypervisorId,
            @Param("gpuId") String gpuId,
            @Param("status") String status,
            @Param("reservationId") String reservationId,
            @Param("instanceId") String instanceId,
            @Param("version") long version,
            @Param("updatedAt") Timestamp updatedAt);
}
