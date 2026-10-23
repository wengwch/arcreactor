package cn.veryai.arcreactor.util;

/** Application memory uses whole GB, with 1 GB = 1024 MB. */
public final class MemoryUnits {
    private MemoryUnits() {}

    /** Read both current GB fields and MB fields in persisted events/snapshots. */
    public static long readGigabytes(Long memoryGb, Long legacyMemoryMb) {
        if (legacyMemoryMb != null) {
            if (legacyMemoryMb % 1024 != 0) {
                throw new IllegalArgumentException("legacy memory must be a whole number of GB");
            }
            long converted = legacyMemoryMb / 1024;
            if (memoryGb != null && memoryGb != converted) {
                throw new IllegalArgumentException("conflicting GB and legacy MB memory values");
            }
            return converted;
        }
        if (memoryGb == null) throw new IllegalArgumentException("memory in GB is required");
        return memoryGb;
    }
}
