package jvmprobe255;

import java.util.Objects;

/**
 * Immutable identity of an instrumented method: internal class name, method name and JVM descriptor.
 */
public final class MethodKey {
    private final String className;
    private final String methodName;
    private final String descriptor;

    public MethodKey(String className, String methodName, String descriptor) {
        this.className = Objects.requireNonNull(className, "className");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    /** Internal JVM class name, e.g. {@code com/acme/Demo}. */
    public String className() {
        return className;
    }

    public String methodName() {
        return methodName;
    }

    /** JVM method descriptor, e.g. {@code (IJ)Ljava/lang/String;}. */
    public String descriptor() {
        return descriptor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MethodKey)) {
            return false;
        }
        MethodKey other = (MethodKey) o;
        return className.equals(other.className)
                && methodName.equals(other.methodName)
                && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, methodName, descriptor);
    }

    @Override
    public String toString() {
        return className + "." + methodName + descriptor;
    }
}
