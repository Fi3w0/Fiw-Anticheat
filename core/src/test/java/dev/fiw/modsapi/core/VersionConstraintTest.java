package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.verify.VersionConstraint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionConstraintTest {

    @Test
    void anyMatchesEverything() {
        assertTrue(VersionConstraint.parse(null).matches("1.2.3"));
        assertTrue(VersionConstraint.parse("*").matches("anything"));
        assertTrue(VersionConstraint.parse("any").matches("0.0.1"));
    }

    @Test
    void exactMatch() {
        VersionConstraint c = VersionConstraint.parse("1.2.0");
        assertTrue(c.matches("1.2.0"));
        assertFalse(c.matches("1.2.1"));
    }

    @Test
    void atLeast() {
        VersionConstraint c = VersionConstraint.parse(">=1.2.0");
        assertTrue(c.matches("1.2.0"));
        assertTrue(c.matches("1.2.5"));
        assertTrue(c.matches("2.0.0"));
        assertFalse(c.matches("1.1.9"));
    }

    @Test
    void numericSegmentsNotLexicographic() {
        assertTrue(VersionConstraint.compare("0.10.0", "0.9.0") > 0);
        assertEquals(0, VersionConstraint.compare("1.2", "1.2.0"));
    }
}
