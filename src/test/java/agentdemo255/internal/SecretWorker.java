package agentdemo255.internal;

/** Lives under the excluded prefix; it must never appear in the report. */
public final class SecretWorker {

    private SecretWorker() {
    }

    public static long compute(int depth) {
        if (depth <= 0) {
            return 7L;
        }
        return depth + compute(depth - 1);
    }
}
