package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleUITest {
    @Test
    void progressBarClampsPercentagesAndUsesFixedWidth() {
        ConsoleUI ui = new ConsoleUI(new ConsoleStyle(false));

        assertEquals("---------- 0.0%", ui.progressBar(-10, 10));
        assertEquals("#####----- 50.0%", ui.progressBar(50, 10));
        assertEquals("########## 100.0%", ui.progressBar(150, 10));
    }

    @Test
    void mainMenuDisplaysZeroAsTheExitChoice() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured));
            new ConsoleUI(new ConsoleStyle(false)).mainMenu(List.of("Dashboard", "Help"));
        } finally {
            System.setOut(original);
        }

        assertTrue(captured.toString().contains("0  Exit"));
        assertTrue(captured.toString().contains("01  Dashboard"));
        assertTrue(captured.toString().contains("+"));
    }
}
