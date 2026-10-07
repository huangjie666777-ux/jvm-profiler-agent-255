package jvmprobe255.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOptionsTest {

    @Test
    void parsesFullOptions() {
        AgentOptions options = AgentOptions.parse(
                "packages=com/acme,com/other;excludes=com/acme/x;report=build/out/report.json");
        assertEquals(List.of("com/acme", "com/other"), options.includes());
        assertEquals(List.of("com/acme/x"), options.excludes());
        assertEquals("build/out/report.json", options.reportPath().toString().replace('\\', '/'));
    }

    @Test
    void excludesIsOptional() {
        AgentOptions options = AgentOptions.parse("packages=com/acme;report=r.json");
        assertTrue(options.excludes().isEmpty());
    }

    @Test
    void rejectsIllegalArguments() {
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse(null));
        assertThrows(IllegalArgumentException.class, () -> AgentOptions.parse(""));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("packages=com/acme"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("report=r.json"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("packages=;report=r.json"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("packages=com/acme;report="));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("packages=com/acme;report=r.json;packages=org/x"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("weird"));
        assertThrows(IllegalArgumentException.class,
                () -> AgentOptions.parse("unknown=1;packages=com/acme;report=r.json"));
    }
}
