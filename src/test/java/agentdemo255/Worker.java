package agentdemo255;

/** Profiled application code; makes no reference to the jvmprobe255 SDK. */
public class Worker {

    public long factorial(int n) {
        if (n <= 1) {
            return 1L;
        }
        return n * factorial(n - 1);
    }

    public int risky(int input) {
        if (input < 0) {
            throw new IllegalStateException("boom:" + input);
        }
        return input * 2;
    }

    public long callExcluded(int depth) {
        return agentdemo255.internal.SecretWorker.compute(depth);
    }
}
