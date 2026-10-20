package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ConsoleInputTest {
    @Test
    void parsesIntegerAndIsoDate() {
        ConsoleInput input = input("12\n2026-10-19\n");

        assertEquals(12, input.readInt(""));
        assertEquals(LocalDate.of(2026, 10, 19), input.readDate(""));
    }

    @Test
    void rejectsEmptyAndMalformedNumbers() {
        ConsoleInput empty = input("\n");
        ConsoleInput malformed = input("abc\n");

        assertEquals("Enter a valid whole number.", assertThrows(IllegalArgumentException.class,
            () -> empty.readLong("")).getMessage());
        assertEquals("Enter a valid whole number.", assertThrows(IllegalArgumentException.class,
            () -> malformed.readLong("")).getMessage());
    }

    @Test
    void rejectsOutOfRangeIntegerAndMalformedDate() {
        ConsoleInput outOfRange = input("2147483648\n");
        ConsoleInput malformedDate = input("19-10-2026\n");

        assertEquals("Enter a whole number in the supported range.",
            assertThrows(IllegalArgumentException.class,
                () -> outOfRange.readInt("")).getMessage());
        assertEquals("Enter a valid date in YYYY-MM-DD format.",
            assertThrows(IllegalArgumentException.class,
                () -> malformedDate.readDate("")).getMessage());
    }

    @Test
    void malformedTopLevelChoiceCanBeReportedWithoutTerminatingInputHandling() {
        ConsoleInput input = input("not-a-menu-number\n0\n");

        assertEquals("Enter a valid whole number.", assertThrows(IllegalArgumentException.class,
                () -> input.readInt("")).getMessage());
        assertEquals(0, input.readInt(""));
    }

    @Test
    void reportsClosedInputSeparatelyFromAnEmptyLine() {
        ConsoleInput input = new ConsoleInput(new ByteArrayInputStream(new byte[0]));

        assertEquals("Console input is closed.", assertThrows(ConsoleInputClosedException.class,
                () -> input.readText("")).getMessage());
    }

    @Test
    void passwordFallbackPreservesWhitespaceForNonConsoleInput() {
        ConsoleInput input = input(" secret with spaces \n");

        assertArrayEquals(" secret with spaces ".toCharArray(), input.readPassword(""));
    }

    private ConsoleInput input(String text) {
        return new ConsoleInput(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
