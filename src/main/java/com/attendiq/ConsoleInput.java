package com.attendiq;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public final class ConsoleInput {
    private final Scanner scanner;

    public ConsoleInput(InputStream input) {
        this.scanner = new Scanner(input);
    }

    public String readText(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new ConsoleInputClosedException();
        }
        return scanner.nextLine().trim();
    }

    public long readLong(String prompt) {
        String value = readText(prompt);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid whole number.");
        }
    }

    public int readInt(String prompt) {
        long value = readLong(prompt);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Enter a whole number in the supported range.");
        }
        return (int) value;
    }

    public LocalDate readDate(String prompt) {
        String value = readText(prompt);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Enter a valid date in YYYY-MM-DD format.");
        }
    }
}