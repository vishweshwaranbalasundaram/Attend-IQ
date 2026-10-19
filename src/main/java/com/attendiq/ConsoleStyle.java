package com.attendiq;

public final class ConsoleStyle {
    public enum Tone {
        BRAND("\u001b[38;5;141m"),
        PRIMARY("\u001b[38;5;45m"),
        SUCCESS("\u001b[38;5;82m"),
        WARNING("\u001b[38;5;220m"),
        ERROR("\u001b[38;5;203m"),
        MUTED("\u001b[38;5;245m");

        private final String ansi;

        Tone(String ansi) {
            this.ansi = ansi;
        }
    }

    private static final String RESET = "\u001b[0m";
    private final boolean enabled;

    public ConsoleStyle() {
        this(detectColorSupport());
    }

    public ConsoleStyle(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public String paint(String text, Tone tone) {
        if (!enabled) {
            return text;
        }
        return tone.ansi + text + RESET;
    }

    private static boolean detectColorSupport() {
        String preference = System.getenv("ATTENDIQ_COLOR");
        if ("never".equalsIgnoreCase(preference) || System.getenv("NO_COLOR") != null) {
            return false;
        }
        if ("always".equalsIgnoreCase(preference)) {
            return true;
        }
        return System.console() != null || System.getenv("WT_SESSION") != null
                || "vscode".equalsIgnoreCase(System.getenv("TERM_PROGRAM"))
                || System.getenv("ANSICON") != null;
    }
}