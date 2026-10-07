package demo255.excluded;

/** Lives under an excluded package prefix: must not appear in the agent report. */
public final class Hidden {
    private Hidden() {
    }

    public static void run() {
        System.out.println("hidden work (excluded from profiling)");
    }
}

