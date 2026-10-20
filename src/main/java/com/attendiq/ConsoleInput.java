package com.attendiq;

import java.io.InputStream;
import java.io.Console;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public final class ConsoleInput {
    private final Scanner scanner;
    private final Console console;

    public ConsoleInput(InputStream input) {
        this.scanner = new Scanner(input);
        this.console = input == System.in ? System.console() : null;
    }

    public String readText(String prompt) {
        if (console != null) {
            String value = console.readLine("%s", prompt);
            if (value == null) {
                throw new ConsoleInputClosedException();
            }
            return value.trim();
        }
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new ConsoleInputClosedException();
        }
        return scanner.nextLine().trim();
    }

    public char[] readPassword(String prompt) {
        if (console != null) {
            char[] password = console.readPassword("%s", prompt);
            if (password == null) {
                throw new ConsoleInputClosedException();
            }
            return password;
        }
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            throw new ConsoleInputClosedException();
        }
        return scanner.nextLine().toCharArray();
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
