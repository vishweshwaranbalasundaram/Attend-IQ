package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleTableTest {
    @Test
    void rendersAlignedRowsAndTruncatesLongCells() {
        String table = ConsoleTable.render(List.of("Name", "Status"),
                List.of(List.of("A very long student name", "PRESENT")),
                12, new ConsoleStyle(false));

        assertTrue(table.contains("A very lo..."));
        assertTrue(table.contains("PRESENT"));
        assertTrue(table.contains("+--------------+---------+"));
    }

    @Test
    void colorsStatusCellsOnlyWhenColorIsEnabled() {
        String plain = ConsoleTable.render(List.of("Status"),
                List.of(List.of("ABSENT")), 12, new ConsoleStyle(false));
        String colored = ConsoleTable.render(List.of("Status"),
                List.of(List.of("ABSENT")), 12, new ConsoleStyle(true));

        assertFalse(plain.contains("\u001b"));
        assertTrue(colored.contains("\u001b[38;5;203m"));
    }

    @Test
    void narrowTerminalSwitchesWideTablesToWrappedKeyValueRows() {
        String table = ConsoleTable.renderResponsive(
                List.of("Student registration", "Student name", "Attendance status"),
                List.of(List.of("REC26CS0000000001", "A long student name for narrow console",
                        "ON_DUTY")), 28, 32, new ConsoleStyle(false));

        assertTrue(table.contains("Student registration"));
        assertTrue(table.contains("REC26CS0000000001"));
        assertTrue(table.contains("A long student name"));
        assertTrue(table.contains("ON_DUTY"));
    }
}
