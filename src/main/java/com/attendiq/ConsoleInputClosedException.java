package com.attendiq;

public final class ConsoleInputClosedException extends RuntimeException {
    public ConsoleInputClosedException() {
        super("Console input is closed.");
    }
}