package agentdemo255;

/**
 * Plain classpath demo driven solely by {@code -javaagent}. It imports and
 * calls nothing from the jvmprobe255 SDK. Its excluded sub-package
 * ({@code agentdemo255.internal}) is used to demonstrate exclusion.
 */
public final class DemoMain {

    public static void main(String[] args) {
        Worker worker = new Worker();
        System.out.println("factorial(5) = " + worker.factorial(5));
        System.out.println("risky(21) = " + worker.risky(21));
        try {
            worker.risky(-1);
        } catch (IllegalStateException e) {
            System.out.println("exception preserved: " + e.getMessage());
        }
        System.out.println("via excluded package = " + worker.callExcluded(4));
        System.out.println("demo finished normally");
    }
}
