package com.example;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

final class ConfigLoader {
    private static final Gson GSON = new Gson();

    static RootConfig loadConfig(Path path) throws IOException {
        if (!Files.exists(path)) throw new IOException("Config not found: " + path);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        RootConfig cfg = GSON.fromJson(json, RootConfig.class);
        if (cfg == null) throw new IOException("Invalid config JSON");
        if (cfg.services == null) cfg.services = new java.util.ArrayList<>();
        if (cfg.defaultPollIntervalSec <= 0) cfg.defaultPollIntervalSec = 10;
        if (cfg.stateFile == null || cfg.stateFile.isBlank()) {
            cfg.stateFile = defaultStatePath();
        }
        return cfg;
    }

    static Path resolveConfigPath(String input) {
        return expandPath(input);
    }

    static Path expandPath(String p) {
        if (p == null || p.isBlank()) return Paths.get(defaultStatePath());
        p = expandEnvVars(p);
        if (p.startsWith("~")) p = System.getProperty("user.home") + p.substring(1);
        return Paths.get(p);
    }

    private static String defaultStatePath() {
        String appdata = System.getenv("APPDATA"); // Windows işletim sistemlerindeki adıyla %APPDATA%
        if (appdata != null && !appdata.isBlank()) {
            return appdata + File.separator + "Toaster" + File.separator + "state.json";
        }
        return System.getProperty("user.home") + File.separator + ".toaster" + File.separator + "state.json";
    }

    private static String expandEnvVars(String s) {
        if (s == null) return null;
        for (var e : System.getenv().entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
            s = s.replace("${" + e.getKey() + "}", e.getValue());
        }
        return s;
    }
}
