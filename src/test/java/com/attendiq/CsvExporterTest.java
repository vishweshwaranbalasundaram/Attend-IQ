package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {
    @TempDir
    Path directory;

    @Test
    void quotesAndEscapesCsvValues() throws Exception {
        Path output = directory.resolve("report.csv");

        new CsvExporter().write(output, List.of("Name", "Note"),
                List.of(List.of("Doe, Jane", "said \"present\"\nconfirmed")), false);

        assertEquals("\"Name\",\"Note\"\r\n\"Doe, Jane\",\"said \"\"present\"\"\nconfirmed\"\r\n",
                Files.readString(output, StandardCharsets.UTF_8));
    }

    @Test
    void refusesToOverwriteWithoutExplicitConfirmation() throws Exception {
        Path output = directory.resolve("existing.csv");
        Files.writeString(output, "keep", StandardCharsets.UTF_8);

        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> new CsvExporter().write(output, List.of("header"), List.of(), false));
        assertEquals("keep", Files.readString(output, StandardCharsets.UTF_8));
    }

    @Test
    void overwritesOnlyWhenCallerConfirms() throws Exception {
        Path output = directory.resolve("overwrite.csv");
        Files.writeString(output, "old", StandardCharsets.UTF_8);

        new CsvExporter().write(output, List.of("new"), List.of(), true);

        assertEquals("\"new\"\r\n", Files.readString(output, StandardCharsets.UTF_8));
    }
}