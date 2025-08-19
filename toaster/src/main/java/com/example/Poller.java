package com.example;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

final class Poller {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static void pollOnce(ServiceCfg svc, Map<String,String> state, Path statePath, NotifierBackend backend) {
        try {
            // request tanımla önce
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(svc.request.url))
                    .timeout(Duration.ofMillis(opt(svc.request.timeoutMs, 5000)));
            if ("POST".equalsIgnoreCase(opt(svc.request.method, "GET"))) {
                b.POST(HttpRequest.BodyPublishers.ofString(opt(svc.request.body, "")));
            } else {
                b.GET();
            }
            if (svc.request.headers != null) svc.request.headers.forEach(b::header);

            // Send
            HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                System.err.println("[" + svc.name + "] HTTP " + res.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();

            // "updated" fieldine bak
            boolean updated = getBoolean(root, svc.parse.updatedField);
            if (!Objects.equals(updated, opt(svc.parse.updatedIsTrue, Boolean.TRUE))) return;

            JsonObject data = getObject(root, svc.parse.dataPath);
            if (data == null) return;

            String id      = getString(data, svc.parse.idField);
            String title   = getString(data, svc.parse.titleField);
            String content = getString(data, svc.parse.contentField);
            String link    = getString(data, svc.parse.linkField);
            String icon    = (svc.iconOverride != null && !svc.iconOverride.isBlank())
                    ? svc.iconOverride : getString(data, svc.parse.iconField);


            String signature = (id != null && !id.isBlank()) ? id : (title + "|" + content + "|" + link);
            String seen = state.get(svc.name);
            if (signature != null && signature.equals(seen)) return;

            // NOTIFY
            backend.notify(title, content, icon, link);
            System.out.println("[" + svc.name + "] NEW: " + title + " — " + content);

            state.put(svc.name, signature);
            StateStore.saveState(statePath, state);

        } catch (Exception e) {
            System.err.println("[" + svc.name + "] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- JSON helpers
    private static String getString(JsonObject obj, String field) {
        if (obj == null || field == null) return null;
        JsonElement el = obj.get(field);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
    }
    private static boolean getBoolean(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        return el != null && !el.isJsonNull() && el.getAsBoolean();
    }
    private static JsonObject getObject(JsonObject obj, String field) {
        JsonElement el = obj.get(field);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }
    private static <T> T opt(T v, T def) { return v != null ? v : def; }
}
