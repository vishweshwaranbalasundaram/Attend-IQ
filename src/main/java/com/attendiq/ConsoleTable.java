package com.attendiq;

import java.util.List;

public final class ConsoleTable {
    private static final int PAGE_SIZE = 12;

    private ConsoleTable() {}

    public static void printPaged(List<String> headers, List<? extends List<String>> rows,
                                  ConsoleInput input, ConsoleStyle style) {
        if (rows.isEmpty()) {
            System.out.println(style.paint("No records found.", ConsoleStyle.Tone.MUTED));
            return;
        }
        int rowsPerPage = headers.size() > 5 ? Math.max(1, 72 / headers.size()) : PAGE_SIZE;
        int pageCount = (rows.size() + rowsPerPage - 1) / rowsPerPage;
        for (int page = 0; page < pageCount; page++) {
            int start = page * rowsPerPage;
            int end = Math.min(start + rowsPerPage, rows.size());
            int available = terminalWidth() - (headers.size() * 3 + 1);
            int columnWidth = Math.max(6, Math.min(28, available / headers.size()));
            System.out.println(renderResponsive(headers, rows.subList(start, end), columnWidth,
                    terminalWidth(), style));
            System.out.println(style.paint("Page " + (page + 1) + " of " + pageCount
                    + " | showing " + (start + 1) + "-" + end + " of " + rows.size(),
                    ConsoleStyle.Tone.MUTED));
            if (end < rows.size()) {
                String answer = input.readText("Enter for next page, Q to stop: ");
                if ("q".equalsIgnoreCase(answer)) {
                    return;
                }
            }
        }
    }

    static String renderResponsive(List<String> headers, List<? extends List<String>> rows,
                                   int maximumColumnWidth, int terminalWidth,
                                   ConsoleStyle style) {
        int[] widths = widths(headers, rows, maximumColumnWidth);
        int tableWidth = 1;
        for (int width : widths) {
            tableWidth += width + 3;
        }
        if (tableWidth <= terminalWidth) {
            return render(headers, rows, maximumColumnWidth, style);
        }
        return renderStacked(headers, rows, Math.max(20, terminalWidth - 4), style);
    }

    private static int terminalWidth() {
        try {
            String value = System.getenv("COLUMNS");
            return value == null ? 80 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 80;
        }
    }

    public static String render(List<String> headers, List<? extends List<String>> rows,
                                int maximumColumnWidth, ConsoleStyle style) {
        if (headers.isEmpty()) {
            return "";
        }
        int[] widths = widths(headers, rows, maximumColumnWidth);
        StringBuilder table = new StringBuilder();
        String border = border(widths);
        table.append(border).append(System.lineSeparator());
        table.append(row(headers, widths, style, true)).append(System.lineSeparator());
        table.append(border).append(System.lineSeparator());
        for (List<String> values : rows) {
            table.append(row(values, widths, style, false)).append(System.lineSeparator());
        }
        table.append(border);
        return table.toString();
    }

    private static int[] widths(List<String> headers, List<? extends List<String>> rows,
                                int maximumColumnWidth) {
        int[] widths = new int[headers.size()];
        for (int column = 0; column < headers.size(); column++) {
            widths[column] = Math.min(maximumColumnWidth, Math.max(3, headers.get(column).length()));
            for (List<String> row : rows) {
                String value = cell(row, column);
                widths[column] = Math.min(maximumColumnWidth,
                        Math.max(widths[column], value.length()));
            }
        }
        return widths;
    }

    private static String renderStacked(List<String> headers, List<? extends List<String>> rows,
                                        int lineWidth, ConsoleStyle style) {
        StringBuilder table = new StringBuilder();
        for (List<String> values : rows) {
            table.append(style.paint("-".repeat(lineWidth), ConsoleStyle.Tone.MUTED))
                    .append(System.lineSeparator());
            for (int column = 0; column < headers.size(); column++) {
                String value = cell(values, column);
                for (String headerLine : wrap(headers.get(column), lineWidth)) {
                    table.append(style.paint(headerLine, ConsoleStyle.Tone.PRIMARY))
                        .append(System.lineSeparator());
                }
                for (String valueLine : wrap(value, Math.max(8, lineWidth - 2))) {
                    table.append("  ").append(styleCell(valueLine, value, style))
                        .append(System.lineSeparator());
                }
            }
        }
        return table.toString().stripTrailing();
    }

    private static List<String> wrap(String value, int width) {
        if (value.isEmpty()) {
            return List.of("");
        }
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int split = remaining.lastIndexOf(' ', width);
            if (split <= 0) {
                split = width;
            }
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(split).stripLeading();
        }
        lines.add(remaining);
        return lines;
    }

    private static String row(List<String> values, int[] widths,
                              ConsoleStyle style, boolean heading) {
        StringBuilder rendered = new StringBuilder("|");
        for (int index = 0; index < widths.length; index++) {
            String value = truncate(cell(values, index), widths[index]);
            String padded = " " + value + " ".repeat(widths[index] - value.length()) + " |";
            if (heading) {
                padded = style.paint(padded, ConsoleStyle.Tone.PRIMARY);
            } else {
                padded = styleCell(padded, value, style);
            }
            rendered.append(padded);
        }
        return rendered.toString();
    }

    private static String styleCell(String padded, String value, ConsoleStyle style) {
        return switch (value.toUpperCase()) {
            case "PRESENT", "SUCCESS", "ONLINE" -> style.paint(padded, ConsoleStyle.Tone.SUCCESS);
            case "ABSENT", "FAILED", "OFFLINE" -> style.paint(padded, ConsoleStyle.Tone.ERROR);
            case "ON_DUTY", "LATE" -> style.paint(padded, ConsoleStyle.Tone.PRIMARY);
            case "UNMARKED", "WARNING" -> style.paint(padded, ConsoleStyle.Tone.WARNING);
            default -> padded;
        };
    }

    private static String cell(List<String> values, int index) {
        if (index >= values.size() || values.get(index) == null) {
            return "";
        }
        return values.get(index).replace('\n', ' ').replace('\r', ' ');
    }

    private static String truncate(String value, int width) {
        if (value.length() <= width) {
            return value;
        }
        if (width <= 3) {
            return value.substring(0, width);
        }
        return value.substring(0, width - 3) + "...";
    }

    private static String border(int[] widths) {
        StringBuilder line = new StringBuilder("+");
        for (int width : widths) {
            line.append("-".repeat(width + 2)).append('+');
        }
        return line.toString();
    }
}