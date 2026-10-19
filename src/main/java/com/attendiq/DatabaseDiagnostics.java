package com.attendiq;

public record DatabaseDiagnostics(String databaseName, String serverName,
                                  String serverVersion) {}