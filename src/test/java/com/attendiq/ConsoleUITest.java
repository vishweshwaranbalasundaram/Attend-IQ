package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConsoleUITest {
    @Test
    void progressBarClampsPercentagesAndUsesFixedWidth() {
        ConsoleUI ui = new ConsoleUI(new ConsoleStyle(false));

        assertEquals("---------- 0.0%", ui.progressBar(-10, 10));
        assertEquals("#####----- 50.0%", ui.progressBar(50, 10));
        assertEquals("########## 100.0%", ui.progressBar(150, 10));
    }
}