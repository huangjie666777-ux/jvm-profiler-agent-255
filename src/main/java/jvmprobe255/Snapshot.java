package jvmprobe255;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable, point-in-time view of all method statistics. Only exited invocations are counted.
 */
public final class Snapshot {
    private final Map<MethodKey, MethodStats> stats;

    public Snapshot(Map<MethodKey, MethodStats> stats) {
        Map<MethodKey, MethodStats> copy = new LinkedHashMap<>(stats);
        this.stats = Collections.unmodifiableMap(copy);
    }

    public Map<MethodKey, MethodStats> stats() {
        return stats;
    }

    public MethodStats get(MethodKey key) {
        return stats.get(key);
    }

    public MethodStats get(String internalClassName, String methodName, String descriptor) {
        return stats.get(new MethodKey(internalClassName, methodName, descriptor));
    }

    @Override
    public String toString() {
        return "Snapshot" + stats;
    }
}
