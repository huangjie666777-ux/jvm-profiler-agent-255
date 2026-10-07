package jvmprobe255.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigTest {

    @Test
    void parsesIncludeExcludeAndReport() {
        AgentConfig config = AgentConfig.parse(
                "include=com.example,exclude=com.example.legacy;com.example.gen,report=build/probe.json");
        assertEquals("com.example", config.includePrefix());
        assertEquals(java.util.List.of("com.example.legacy", "com.example.gen"), config.excludePrefixes());
        assertEquals("build/probe.json", config.reportPath());
    }

    @Test
    void matchesOnPackageBoundariesOnly() {
        AgentConfig config = AgentConfig.parse("include=com.example,report=r.json");
        assertTrue(config.includes("com/example/Foo"));
        assertTrue(config.includes("com.example.sub.Bar"));
        assertFalse(config.includes("com.example2.Foo"));
        assertFalse(config.includes("org.other.Foo"));
    }

    @Test
    void excludeWinsOverInclude() {
        AgentConfig config = AgentConfig.parse(
                "include=com.example,exclude=com.example.legacy,report=r.json");
        assertTrue(config.includes("com.example.Main"));
        assertFalse(config.includes("com.example.legacy.Old"));
        assertFalse(config.includes("com.example.legacy.deep.Older"));
    }

    @Test
    void trailingDotsAndSlashesAreNormalized() {
        AgentConfig config = AgentConfig.parse("include=com/example/,report=r.json");
        assertTrue(config.includes("com.example.Foo"));
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.parse(null));
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.parse(""));
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.parse("report=r.json"));
        assertThrows(IllegalArgumentException.class, () -> AgentConfig.parse("include=com.example"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentConfig.parse("include=com.example,report=r.json,unknown=1"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentConfig.parse("include=com.example,include=com.other,report=r.json"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentConfig.parse("include=com.example,report="));
        assertThrows(IllegalArgumentException.class,
                () -> AgentConfig.parse("justastring"));
    }
}

