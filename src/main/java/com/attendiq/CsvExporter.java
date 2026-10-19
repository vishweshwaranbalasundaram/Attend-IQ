package com.attendiq;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class CsvExporter {
    public void write(Path destination, List<String> headers, List<? extends List<String>> rows,
                      boolean overwriteConfirmed) throws IOException {
        Path absolute = destination.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(absolute) && !overwriteConfirmed) {
            throw new java.nio.file.FileAlreadyExistsException(absolute.toString());
        }
        StandardOpenOption[] options = overwriteConfirmed
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE}
                : new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        try (BufferedWriter writer = Files.newBufferedWriter(absolute, StandardCharsets.UTF_8, options)) {
            writeRow(writer, headers);
            for (List<String> row : rows) {
                writeRow(writer, row);
            }
        }
    }

    private void writeRow(BufferedWriter writer, List<String> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            String value = values.get(index) == null ? "" : values.get(index);
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.newLine();
    }
}