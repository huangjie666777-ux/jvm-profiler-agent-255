package jvmprobe255.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Package-boundary class filter used by the startup Java agent.
 *
 * <p>A class matches a prefix {@code com/acme} only when it lives in exactly
 * that package or a sub-package ({@code com/acme/Foo}, {@code com/acme/util/F});
 * a sibling such as {@code com/acme2/Foo} never matches. Exclusions take
 * precedence over inclusions. JDK classes, ASM (original or shaded inside the
 * agent) and the SDK itself are always skipped.</p>
 */
public final class PackageFilter {
    private static final String SDK_PACKAGE = "jvmprobe255/";
    private static final String ASM_PACKAGE = "org/objectweb/asm/";
    private static final String SHADED_ASM_PACKAGE = "jvmprobe255/shaded/asm/";

    private final List<String> includes;
    private final List<String> excludes;

    public PackageFilter(List<String> includes, List<String> excludes) {
        this.includes = List.copyOf(normalize(includes));
        this.excludes = List.copyOf(normalize(excludes));
    }

    private static List<String> normalize(List<String> prefixes) {
        List<String> result = new ArrayList<>();
        for (String prefix : prefixes) {
            String trimmed = prefix.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String internal = trimmed.replace('.', '/');
            while (internal.endsWith("/")) {
                internal = internal.substring(0, internal.length() - 1);
            }
            if (!internal.isEmpty()) {
                result.add(internal);
            }
        }
        return result;
    }

    /**
     * @param internalClassName binary class name in internal form, e.g. {@code com/acme/Foo}
     * @return {@code true} when the class must be instrumented
     */
    public boolean shouldTransform(String internalClassName) {
        if (internalClassName == null) {
            return false;
        }
        String name = internalClassName.replace('.', '/');
        if (name.startsWith(SDK_PACKAGE)
                || name.startsWith(ASM_PACKAGE)
                || name.startsWith(SHADED_ASM_PACKAGE)
                || isJdkClass(name)) {
            return false;
        }
        if (matchesAny(name, excludes)) {
            return false;
        }
        return matchesAny(name, includes);
    }

    private static boolean isJdkClass(String name) {
        return name.startsWith("java/")
                || name.startsWith("javax/")
                || name.startsWith("jdk/")
                || name.startsWith("sun/")
                || name.startsWith("com/sun/")
                || name.startsWith("module-info");
    }

    private static boolean matchesAny(String internalName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (matchesBoundary(internalName, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBoundary(String internalName, String prefix) {
        if (!internalName.startsWith(prefix)) {
            return false;
        }
        int length = prefix.length();
        if (internalName.length() == length) {
            return true;
        }
        char next = internalName.charAt(length);
        // Next char must be the package separator; "com/acme2/X" must not match "com/acme".
        return next == '/';
    }
}
