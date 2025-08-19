package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class StateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Map<String, String> loadState(Path path) {
        try {
            if (!Files.exists(path)) return new ConcurrentHashMap<>();
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, String> m = GSON.fromJson(json, Map.class);
            return new ConcurrentHashMap<>(m != null ? m : Map.of());
        } catch (Exception e) {
            return new ConcurrentHashMap<>();
        }
    }

    static void saveState(Path path, Map<String, String> state) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            String json = GSON.toJson(state);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("[state] " + e.getMessage());
        }
    }
}
