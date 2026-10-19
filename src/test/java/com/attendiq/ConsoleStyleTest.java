package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConsoleStyleTest {
    @Test
    void plainModeContainsNoAnsiEscapes() {
        ConsoleStyle style = new ConsoleStyle(false);

        assertFalse(style.paint("Attend-IQ", ConsoleStyle.Tone.BRAND).contains("\u001b"));
    }

    @Test
    void coloredModeAddsToneAndResetCodes() {
        ConsoleStyle style = new ConsoleStyle(true);
        String rendered = style.paint("Saved", ConsoleStyle.Tone.SUCCESS);

        assertTrue(rendered.startsWith("\u001b["));
        assertTrue(rendered.endsWith("\u001b[0m"));
    }
}
