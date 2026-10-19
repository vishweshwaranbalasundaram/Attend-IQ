package com.attendiq;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ConsoleUI {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ConsoleStyle style;

    public ConsoleUI(ConsoleStyle style) {
        this.style = style;
    }

    public ConsoleStyle style() {
        return style;
    }

    public void header(String databaseStatus) {
        int columns = terminalWidth();
        if (columns < 68) {
            System.out.println(style.paint("ATTEND-IQ | SMART ATTENDANCE", ConsoleStyle.Tone.BRAND));
            System.out.println(style.paint("Java 25 | JDBC | MySQL | v1.0.0", ConsoleStyle.Tone.MUTED));
            System.out.println("Database: " + databaseStatus + " | " + LocalDateTime.now().format(CLOCK));
            section("MAIN MENU");
            return;
        }
        int width = Math.min(columns - 2, 88);
        String rule = "+" + "-".repeat(width - 2) + "+";
        System.out.println(style.paint(rule, ConsoleStyle.Tone.MUTED));
        centered("ATTEND-IQ", width, ConsoleStyle.Tone.BRAND);
        centered("SMART ATTENDANCE MANAGEMENT SYSTEM", width, ConsoleStyle.Tone.PRIMARY);
        centered("JAVA OOP  |  JDBC  |  MYSQL  |  VERSION 1.0.0", width, ConsoleStyle.Tone.MUTED);
        String status = "Database: " + databaseStatus + "    " + LocalDateTime.now().format(CLOCK);
        System.out.println("| " + pad(status, width - 4) + " |");
        System.out.println(style.paint(rule, ConsoleStyle.Tone.MUTED));
    }

    public void section(String title) {
        System.out.println();
        int availableTitle = Math.max(12, terminalWidth() - 8);
        String visibleTitle = title.length() > availableTitle
            ? title.substring(0, Math.max(1, availableTitle - 3)) + "..." : title;
        int ruleLength = Math.max(2, Math.min(42, terminalWidth() - visibleTitle.length() - 5));
        System.out.println(style.paint("-- " + visibleTitle + " " + "-".repeat(ruleLength),
                ConsoleStyle.Tone.PRIMARY));
    }

    public void menu(String title, List<String> options) {
        section(title);
        for (int index = 0; index < options.size(); index++) {
            System.out.printf("  %s  %s%n", style.paint(String.format("%2d", index + 1),
                    ConsoleStyle.Tone.PRIMARY), options.get(index));
        }
        System.out.printf("  %s  %s%n", style.paint(" 0", ConsoleStyle.Tone.MUTED), "Back / Exit");
    }

    public void mainMenu(List<String> options) {
        section("MAIN MENU");
        int width = Math.max(40, Math.min(76, terminalWidth() - 4));
        String border = "+" + "-".repeat(width - 2) + "+";
        System.out.println(style.paint(border, ConsoleStyle.Tone.MUTED));
        for (int index = 0; index < options.size(); index++) {
            menuRow(String.format("%02d", index + 1), options.get(index), width,
                    ConsoleStyle.Tone.PRIMARY);
        }
        menuRow(" 0", "Exit", width, ConsoleStyle.Tone.MUTED);
        System.out.println(style.paint(border, ConsoleStyle.Tone.MUTED));
    }

    public void panel(String title, List<String> details) {
        int contentWidth = details.stream().mapToInt(String::length).max().orElse(0) + 4;
        int width = Math.max(28, Math.min(84,
                Math.min(terminalWidth() - 2, Math.max(28, contentWidth))));
        String border = "+" + "-".repeat(width - 2) + "+";
        System.out.println(style.paint(border, ConsoleStyle.Tone.MUTED));
        System.out.println("| " + pad(title, width - 4) + " |");
        System.out.println(style.paint(border, ConsoleStyle.Tone.MUTED));
        for (String detail : details) {
            System.out.println("| " + pad(detail, width - 4) + " |");
        }
        System.out.println(style.paint(border, ConsoleStyle.Tone.MUTED));
    }

    public void success(String message) {
        printMessage("SUCCESS", message, ConsoleStyle.Tone.SUCCESS);
    }

    public void info(String message) {
        printMessage("INFO", message, ConsoleStyle.Tone.PRIMARY);
    }

    public void warning(String message) {
        printMessage("WARNING", message, ConsoleStyle.Tone.WARNING);
    }

    public void error(String message) {
        printMessage("ERROR", message, ConsoleStyle.Tone.ERROR);
    }

    public boolean confirm(ConsoleInput input, String prompt) {
        while (true) {
            String answer = input.readText(prompt + " [y/N]: ");
            if (answer.isEmpty() || "n".equalsIgnoreCase(answer) || "no".equalsIgnoreCase(answer)) {
                return false;
            }
            if ("y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer)) {
                return true;
            }
            warning("Enter Y or N.");
        }
    }

    public String progressBar(double percentage, int width) {
        double bounded = Math.max(0, Math.min(100, percentage));
        int filled = (int) Math.round(width * bounded / 100.0);
        return style.paint("#".repeat(filled), ConsoleStyle.Tone.SUCCESS)
                + style.paint("-".repeat(width - filled), ConsoleStyle.Tone.MUTED)
                + String.format(" %.1f%%", bounded);
    }

    private void centered(String text, int width, ConsoleStyle.Tone tone) {
        int inner = width - 4;
        int left = Math.max(0, (inner - text.length()) / 2);
        System.out.println("| " + " ".repeat(left)
                + style.paint(text, tone) + pad("", inner - left - text.length()) + " |");
    }

    private void menuRow(String number, String label, int width, ConsoleStyle.Tone tone) {
        int innerWidth = width - 4;
        String prefix = " " + number + "  ";
        String visible = prefix + label;
        String fitted = visible.length() <= innerWidth ? visible
                : visible.substring(0, Math.max(0, innerWidth - 3)) + "...";
        int padding = Math.max(0, innerWidth - fitted.length());
        System.out.println("| " + style.paint(prefix, tone) + fitted.substring(prefix.length())
                + " ".repeat(padding) + " |");
    }

    private void printMessage(String label, String message, ConsoleStyle.Tone tone) {
        String prefix = label + "  ";
        int lineWidth = Math.max(40, terminalWidth() - 4);
        int available = Math.max(12, lineWidth - prefix.length());
        StringBuilder line = new StringBuilder(prefix);
        int current = 0;
        for (String word : message.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (current > 0 && current + 1 + word.length() > available) {
                System.out.println(style.paint(line.toString(), tone));
                line = new StringBuilder(" ".repeat(prefix.length()));
                current = 0;
            }
            if (current > 0) {
                line.append(' ');
                current++;
            }
            line.append(word);
            current += word.length();
        }
        System.out.println(style.paint(line.toString(), tone));
    }

    private int terminalWidth() {
        try {
            String value = System.getenv("COLUMNS");
            return value == null ? 80 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 80;
        }
    }

    private String pad(String value, int width) {
        if (value.length() >= width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }
}
