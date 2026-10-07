package jvmprobe255.agent;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsed {@code premain} agent options.
 *
 * <p>Syntax: {@code packages=<p>{,<p>}[;excludes=<x>{,<x>}];report=<path>}.
 * List items are separated by commas or semicolons. Both {@code packages} (at
 * least one prefix) and {@code report} (non-empty path) are required;
 * {@code excludes} is optional. Illegal arguments make {@link #parse(String)}
 * throw {@link IllegalArgumentException} before the application main method
 * runs.</p>
 */
public final class AgentOptions {
    private final List<String> includes;
    private final List<String> excludes;
    private final Path reportPath;

    private AgentOptions(List<String> includes, List<String> excludes, Path reportPath) {
        this.includes = List.copyOf(includes);
        this.excludes = List.copyOf(excludes);
        this.reportPath = reportPath;
    }

    public List<String> includes() {
        return includes;
    }

    public List<String> excludes() {
        return excludes;
    }

    public Path reportPath() {
        return reportPath;
    }

    public PackageFilter newFilter() {
        return new PackageFilter(includes, excludes);
    }

    public static AgentOptions parse(String agentArgs) {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "jvmprobe255 agent requires options: packages=<app prefixes>;report=<path>[;excludes=<prefixes>]");
        }
        List<String> packageValues = null;
        List<String> excludeValues = null;
        String reportValue = null;
        Set<String> seenKeys = new LinkedHashSet<>();

        for (String pair : agentArgs.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(
                        "bad agent option \"" + trimmed + "\": expected key=value");
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (!seenKeys.add(key)) {
                throw new IllegalArgumentException("duplicate agent option key: " + key);
            }
            switch (key) {
                case "packages" -> packageValues = splitPrefixes(value, key);
                case "excludes" -> excludeValues = splitPrefixes(value, key);
                case "report" -> reportValue = checkNonEmpty(value, key);
                default -> throw new IllegalArgumentException("unknown agent option key: " + key);
            }
        }

        if (packageValues == null || packageValues.isEmpty()) {
            throw new IllegalArgumentException(
                    "agent option \"packages\" is required and non-empty");
        }
        if (reportValue == null) {
            throw new IllegalArgumentException(
                    "agent option \"report\" is required and non-empty");
        }
        Path path;
        try {
            path = Paths.get(reportValue);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "agent option \"report\" is not a valid path: " + reportValue, e);
        }
        return new AgentOptions(packageValues,
                excludeValues == null ? List.of() : excludeValues, path);
    }

    private static String checkNonEmpty(String value, String key) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "agent option \"" + key + "\" must be non-empty");
        }
        return value;
    }

    private static List<String> splitPrefixes(String value, String key) {
        String checked = checkNonEmpty(value, key);
        List<String> result = new ArrayList<>();
        for (String item : checked.split("[,;]")) {
            String normalized = item.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException(
                        "agent option \"" + key + "\" contains an empty prefix");
            }
            result.add(normalized);
        }
        return result;
    }
}
