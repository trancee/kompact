package com.google.devtools.ksp.impl;

import java.util.List;

public final class KSPLoader {
    private KSPLoader() {}

    public static Object loadAndRunKSP(byte[] config, List<?> providers, int logLevel) {
        return switch (System.getProperty("kompact.test.ksp.loader.behavior", "success")) {
            case "throw" -> throw new IllegalStateException("test KSP processor failure");
            case "wrong-type" -> "not an exit code";
            case "failure" -> 1;
            default -> 0;
        };
    }
}
