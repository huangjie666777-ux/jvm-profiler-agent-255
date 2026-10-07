package jvmprobe255.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parsed {@code -javaagent} arguments.
 *
 * <p>Format: {@code include=<pkgPrefix>[,exclude=<p1;p2>],report=<path>} where
 * keys are separated by commas and multiple exclude prefixes by semicolons, e.g.
 * {@code include=com.example,exclude=com.example.legacy;com.example.gen,report=build/probe.json}.
 * {@code include} and {@code report} are required; {@code exclude} is optional.
 * Prefixes use dotted package names and match on package boundaries: prefix
 * {@code com.example} matches {@code com.example} itself and
 * {@code com.example.anything} but never {@code com.example2}. Excludes win
 * over includes.</p>
 */
public final class AgentConfig {
    private final String includePrefix;
    private final List<String> excludePrefixes;
    private final String reportPath;

    private AgentConfig(String includePrefix, List<String> excludePrefixes, String reportPath) {
        this.includePrefix = includePrefix;
        this.excludePrefixes = excludePrefixes;
        this.reportPath = reportPath;
    }

    public static AgentConfig parse(String args) {
        if (args == null || args.isBlank()) {
            throw new IllegalArgumentException(
                    "missing agent arguments; expected include=<pkg>,report=<path>[,exclude=<p1;p2>]");
        }
        String include = null;
        String report = null;
        List<String> excludes = new ArrayList<>();
        for (String pair : args.split(",")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed agent argument (expected key=value): " + trimmed);
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("empty value for agent argument: " + key);
            }
            switch (key) {
                case "include" -> {
                    if (include != null) {
                        throw new IllegalArgumentException("duplicate agent argument: include");
                    }
                    include = normalizePrefix(value);
                }
                case "report" -> {
                    if (report != null) {
                        throw new IllegalArgumentException("duplicate agent argument: report");
                    }
                    report = value;
                }
                case "exclude" -> {
                    for (String prefix : value.split(";")) {
                        String p = normalizePrefix(prefix.trim());
                        if (!p.isEmpty()) {
                            excludes.add(p);
                        }
                    }
                }
                default -> throw new IllegalArgumentException("unknown agent argument: " + key);
            }
        }
        if (include == null) {
            throw new IllegalArgumentException("missing required agent argument: include=<package prefix>");
        }
        if (report == null) {
            throw new IllegalArgumentException("missing required agent argument: report=<output path>");
        }
        return new AgentConfig(include, Collections.unmodifiableList(excludes), report);
    }

    /** Strips a trailing dot so {@code com.example.} and {@code com.example} behave identically. */
    private static String normalizePrefix(String prefix) {
        String p = Objects.requireNonNull(prefix, "prefix").replace('/', '.');
        while (p.endsWith(".")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** Package-boundary match: exact package or a nested package of the prefix. */
    static boolean matchesPackage(String prefix, String dottedName) {
        return dottedName.equals(prefix) || dottedName.startsWith(prefix + ".");
    }

    /**
     * Decides whether a class (internal or dotted name) is eligible for profiling:
     * inside the include prefix, outside every exclude prefix.
     */
    public boolean includes(String className) {
        String dotted = className.replace('/', '.');
        if (!matchesPackage(includePrefix, dotted)) {
            return false;
        }
        for (String exclude : excludePrefixes) {
            if (matchesPackage(exclude, dotted)) {
                return false;
            }
        }
        return true;
    }

    public String includePrefix() {
        return includePrefix;
    }

    public List<String> excludePrefixes() {
        return excludePrefixes;
    }

    public String reportPath() {
        return reportPath;
    }
}

