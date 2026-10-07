package jvmprobe255.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageFilterTest {

    private final PackageFilter filter = new PackageFilter(
            List.of("com/acme"), List.of("com/acme/internal"));

    @Test
    void matchesByPackageBoundaryOnly() {
        assertTrue(filter.shouldTransform("com/acme/Foo"));
        assertTrue(filter.shouldTransform("com/acme/util/Foo"));
        assertFalse(filter.shouldTransform("com/acme2/Foo"));
        assertFalse(filter.shouldTransform("org/other/Foo"));
    }

    @Test
    void exclusionWinsOverInclusion() {
        assertFalse(filter.shouldTransform("com/acme/internal/Secret"));
        assertFalse(filter.shouldTransform("com/acme/internal/deep/Secret"));
    }

    @Test
    void jdkAsmAndSdkAreAlwaysSkipped() {
        assertFalse(filter.shouldTransform("java/lang/String"));
        assertFalse(filter.shouldTransform("javax/net/Socket"));
        assertFalse(filter.shouldTransform("jdk/internal/misc/Unsafe"));
        assertFalse(filter.shouldTransform("sun/misc/Unsafe"));
        assertFalse(filter.shouldTransform("jvmprobe255/Profiler"));
        assertFalse(filter.shouldTransform("org/objectweb/asm/ClassReader"));
        assertFalse(filter.shouldTransform("jvmprobe255/shaded/asm/ClassReader"));
    }

    @Test
    void acceptsDottedPrefixes() {
        PackageFilter dotted = new PackageFilter(
                List.of("com.acme"), List.of("com.acme.internal"));
        assertTrue(dotted.shouldTransform("com/acme/Foo"));
        assertFalse(dotted.shouldTransform("com/acme/internal/X"));
    }
}
