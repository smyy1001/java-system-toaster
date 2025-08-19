// package com.example;

// import com.google.gson.*;
// import java.net.URI;
// import java.net.http.*;
// import java.time.Duration;
// import java.util.*;
// import java.io.*;

// public class Notifier {
//     private static final HttpClient client = HttpClient.newBuilder()
//             .connectTimeout(Duration.ofSeconds(5))
//             .build();

//     private static final String BASE = "http://localhost:5000";
//     private static final List<String> SERVICES = List.of("alpha", "beta", "gamma");

//     public static void main(String[] args) throws Exception {
//         System.out.println("Polling " + SERVICES + " every 10s...");
//         while (true) {
//             for (String service : SERVICES) {
//                 try {
//                     JsonObject update = pollService(service);
//                     if (update != null) {
//                         showNotificationCrossPlatform(update);
//                     }
//                     // else {
//                     //     System.out.println("[" + service + "] no update");
//                     // }
//                 } catch (Exception e) {
//                     System.err.println("Error polling " + service + ": " + e);
//                 }
//             }
//             Thread.sleep(10_000);
//         }
//     }

//     private static JsonObject pollService(String service) throws Exception {
//         HttpRequest req = HttpRequest.newBuilder()
//                 .uri(URI.create(BASE + "/update?service=" + service))
//                 .timeout(Duration.ofSeconds(5))
//                 .build();

//         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
//         JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();

//         if (json.get("updated").getAsBoolean()) {
//             return json.getAsJsonObject("data");
//         }
//         return null;
//     }

//     // private static void showToast(JsonObject data) throws IOException, InterruptedException {
//     //     String title = data.get("title").getAsString();
//     //     String content = data.get("content").getAsString();
//     //     String icon = data.get("icon").getAsString();
//     //     String link = data.get("link").getAsString();

//     //     // PowerShell command using BurntToast (built into modern Windows via module)
//     //     String script = String.format(
//     //         "powershell.exe -Command \"[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]; " +
//     //         "$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastImageAndText02); " +
//     //         "$toastXml = $template; " +
//     //         "$toastXml.GetElementsByTagName('text').Item(0).AppendChild($toastXml.CreateTextNode('%s')) | Out-Null; " +
//     //         "$toastXml.GetElementsByTagName('text').Item(1).AppendChild($toastXml.CreateTextNode('%s')) | Out-Null; " +
//     //         "$toastXml.GetElementsByTagName('image').Item(0).SetAttribute('src','%s'); " +
//     //         "$toast = [Windows.UI.Notifications.ToastNotification]::new($toastXml); " +
//     //         "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JavaNotifier').Show($toast)\"",
//     //         escapeForPS(title), escapeForPS(content), escapeForPS(icon)
//     //     );

//     //     new ProcessBuilder("cmd.exe", "/c", script).inheritIO().start().waitFor();

//     //     System.out.println("[TOAST] " + title + " - " + content + " (" + link + ")");
//     // }

//     private static void showNotificationCrossPlatform(JsonObject data) throws IOException, InterruptedException {
//         String title = data.get("title").getAsString();
//         String content = data.get("content").getAsString();
//         String icon = data.get("icon").getAsString();

//         String os = System.getProperty("os.name").toLowerCase();

//         if (os.contains("win")) {
//             // Windows: use PowerShell toast
//             String script = String.format(
//                 "powershell.exe -Command \"[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]; " +
//                 "$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastImageAndText02); " +
//                 "$toastXml = $template; " +
//                 "$toastXml.GetElementsByTagName('text').Item(0).AppendChild($toastXml.CreateTextNode('%s')) | Out-Null; " +
//                 "$toastXml.GetElementsByTagName('text').Item(1).AppendChild($toastXml.CreateTextNode('%s')) | Out-Null; " +
//                 "$toastXml.GetElementsByTagName('image').Item(0).SetAttribute('src','%s'); " +
//                 "$toast = [Windows.UI.Notifications.ToastNotification]::new($toastXml); " +
//                 "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JavaNotifier').Show($toast)\"",
//                 escapeForPS(title), escapeForPS(content), escapeForPS(icon)
//             );
//             new ProcessBuilder("cmd.exe", "/c", script).inheritIO().start().waitFor();
//         } else if (os.contains("linux")) {
//             // Ubuntu/Linux: use notify-send
//             new ProcessBuilder("notify-send", title, content, "-i", icon)
//                     .inheritIO().start().waitFor();
//         } else {
//             // Fallback: just log
//             System.out.println("[NOTIFY] " + title + " - " + content);
//         }
//     }


//     private static String escapeForPS(String input) {
//         return input.replace("'", "''").replace("\"", "`\"");
//     }
// }





package com.example;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Self-contained notifier that:
 * - reads services from services.json (path can be overridden with -Dconfig=/path/to/services.json)
 * - polls each service on its own interval (default 10s)
 * - de-dupes by id/signature and persists lastSeen per service
 * - shows native Windows toast (via PowerShell) or Linux notify-send
 */
public class Notifier {

    // ---- HTTP client --------------------------------------------------------
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ---- Entry point --------------------------------------------------------
    public static void main(String[] args) throws Exception {
        Path cfgPath = resolveConfigPath(System.getProperty("config", "services.json"));
        RootConfig cfg = loadConfig(cfgPath);

        Map<String, String> lastSeen = loadState(expandPath(cfg.stateFile));
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(Math.max(2, cfg.services.size()));

        System.out.println("[notifier] loaded " + cfg.services.size() + " service(s)");
        for (ServiceCfg svc : cfg.services) {
            if (!Boolean.TRUE.equals(svc.enabled)) continue;
            int interval = (svc.pollIntervalSec != null) ? svc.pollIntervalSec : cfg.defaultPollIntervalSec;
            long initialDelayMs = ThreadLocalRandom.current().nextLong(0, 2000); // jitter

            exec.scheduleAtFixedRate(() -> pollOnce(svc, lastSeen, expandPath(cfg.stateFile)),
                    initialDelayMs, interval * 1000L, TimeUnit.MILLISECONDS);

            System.out.println("  • " + svc.name + " every " + interval + "s → " + svc.request.url);
        }

        // Optional: watch for config changes and print a hint (simple)
        watchConfigFile(cfgPath);

        // Keep main alive
        // noinspection InfiniteLoopStatement
        for (;;) Thread.sleep(60_000);
    }

    // ---- Core polling -------------------------------------------------------
    private static void pollOnce(ServiceCfg svc, Map<String, String> lastSeen, Path statePath) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(svc.request.url))
                    .timeout(Duration.ofMillis(opt(svc.request.timeoutMs, 5000)));

            if ("POST".equalsIgnoreCase(opt(svc.request.method, "GET"))) {
                b.POST(HttpRequest.BodyPublishers.ofString(opt(svc.request.body, "")));
            } else {
                b.GET();
            }
            if (svc.request.headers != null) {
                svc.request.headers.forEach(b::header);
            }

            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                System.err.println("[" + svc.name + "] HTTP " + res.statusCode());
                return;
            }

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();

            // Check "updated" flag
            boolean updated = getBoolean(root, svc.parse.updatedField);
            if (updated != opt(svc.parse.updatedIsTrue, Boolean.TRUE)) {
                // no update this tick
                return;
            }

            // Navigate to data object
            JsonObject data = getObject(root, svc.parse.dataPath);
            if (data == null) return;

            // Extract fields
            String id = getString(data, svc.parse.idField);
            String title = getString(data, svc.parse.titleField);
            String content = getString(data, svc.parse.contentField);
            String link = getString(data, svc.parse.linkField);
            String icon = (svc.iconOverride != null && !svc.iconOverride.isBlank())
                    ? svc.iconOverride
                    : getString(data, svc.parse.iconField);

            // De-dupe
            String key = svc.name;
            String signature = (id != null && !id.isBlank()) ? id : (title + "|" + content + "|" + link);
            String seen = lastSeen.get(key);
            if (signature != null && signature.equals(seen)) {
                return; // already notified
            }

            // Notify
            showNotificationCrossPlatform(title, content, icon, link);
            System.out.println("[" + svc.name + "] NEW: " + title + " — " + content);

            // Persist state
            lastSeen.put(key, signature);
            saveState(statePath, lastSeen);

        } catch (Exception e) {
            System.err.println("[" + svc.name + "] " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ---- Notifications (Windows + Linux) -----------------------------------
    private static void showNotificationCrossPlatform(String title, String content, String icon, String link)
            throws IOException, InterruptedException {

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            // Windows: use PowerShell to show a toast with optional image
            String safeTitle = escapeForPS(title == null ? "" : title);
            String safeBody = escapeForPS(content == null ? "" : content);
            String safeIcon = escapeForPS(icon == null ? "" : icon);

            String ps = "powershell.exe -NoProfile -Command \""
                    + "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; "
                    + "$template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastImageAndText02); "
                    + "$xml = $template; "
                    + "$xml.GetElementsByTagName('text').Item(0).AppendChild($xml.CreateTextNode('" + safeTitle + "')) | Out-Null; "
                    + "$xml.GetElementsByTagName('text').Item(1).AppendChild($xml.CreateTextNode('" + safeBody + "')) | Out-Null; "
                    + "if ('" + safeIcon + "' -ne '') { $xml.GetElementsByTagName('image').Item(0).SetAttribute('src','" + safeIcon + "'); } "
                    + "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml); "
                    + "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JavaNotifier').Show($toast)\"";

            new ProcessBuilder("cmd.exe", "/c", ps).inheritIO().start().waitFor();
        } else if (os.contains("linux")) {
            // Linux: notify-send (needs libnotify-bin). Icon can be URL with some desktops; safest is a local path.
            List<String> cmd = new ArrayList<>(List.of("notify-send", title == null ? "" : title, content == null ? "" : content));
            if (icon != null && !icon.isBlank()) {
                cmd.add("-i");
                cmd.add(icon);
            }
            new ProcessBuilder(cmd).inheritIO().start().waitFor();
        } else {
            // Fallback
            System.out.println("[NOTIFY] " + title + " - " + content);
        }

        // Optionally: handle link click by registering a protocol/toast activation.
        // Simpler approach: include the link in the content.
    }

    private static String escapeForPS(String s) {
        return s.replace("'", "''").replace("\"", "`\"");
    }

    // ---- Config + State -----------------------------------------------------
    private static RootConfig loadConfig(Path path) throws IOException {
        if (!Files.exists(path)) throw new FileNotFoundException("Config not found: " + path);
        String json = Files.readString(path, StandardCharsets.UTF_8);
        RootConfig cfg = new Gson().fromJson(json, RootConfig.class);
        if (cfg == null) throw new IllegalStateException("Invalid config JSON");
        if (cfg.services == null) cfg.services = List.of();
        if (cfg.defaultPollIntervalSec <= 0) cfg.defaultPollIntervalSec = 10;
        if (cfg.stateFile == null || cfg.stateFile.isBlank()) {
            cfg.stateFile = defaultStatePath();
        }
        return cfg;
    }

    private static Map<String, String> loadState(Path path) {
        try {
            if (!Files.exists(path)) return new ConcurrentHashMap<>();
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, String> m = new Gson().fromJson(json, Map.class);
            return new ConcurrentHashMap<>(m != null ? m : Map.of());
        } catch (Exception e) {
            return new ConcurrentHashMap<>();
        }
    }

    private static void saveState(Path path, Map<String, String> state) {
        try {
            Files.createDirectories(path.getParent());
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(state);
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            System.err.println("[state] " + e.getMessage());
        }
    }

    private static Path resolveConfigPath(String input) {
        return expandPath(input);  // expandPath already returns a Path
    }

    private static String defaultStatePath() {
        String appdata = System.getenv("APPDATA"); // Windows
        if (appdata != null && !appdata.isBlank()) {
            return appdata + File.separator + "Toaster" + File.separator + "state.json";
        }
        return System.getProperty("user.home") + File.separator + ".toaster" + File.separator + "state.json";
    }

    private static String expandEnvVars(String s) {
        if (s == null) return null;
        // Expand %VAR% (Windows-style) and ${VAR} (Unix-style) minimally
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            s = s.replace("%" + e.getKey() + "%", e.getValue());
            s = s.replace("${" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    private static Path expandPath(String p) {
        if (p == null) return Paths.get(defaultStatePath());
        p = expandEnvVars(p);
        if (p.startsWith("~")) {
            p = System.getProperty("user.home") + p.substring(1);
        }
        return Paths.get(p);
    }

    // ---- JSON helpers -------------------------------------------------------
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

    // ---- Config DTOs --------------------------------------------------------
    static class RootConfig {
        int defaultPollIntervalSec = 10;
        String stateFile; // e.g., %APPDATA%/Toaster/state.json
        List<ServiceCfg> services = List.of();
    }
    static class ServiceCfg {
        String name;
        Boolean enabled = Boolean.TRUE;
        Integer pollIntervalSec;
        String iconOverride;
        RequestCfg request = new RequestCfg();
        ParseCfg parse = new ParseCfg();
    }
    static class RequestCfg {
        String url;
        String method = "GET";
        Map<String, String> headers = new HashMap<>();
        Integer timeoutMs = 5000;
        String body;
    }
    static class ParseCfg {
        String updatedField = "updated";
        @SerializedName("updatedIsTrue")
        Boolean updatedIsTrue = Boolean.TRUE;
        String dataPath = "data";
        String idField = "id";
        String titleField = "title";
        String contentField = "content";
        String linkField = "link";
        String iconField = "icon";
    }

    // ---- Optional: print a hint when services.json changes (no hot-reload yet) --
    private static void watchConfigFile(Path cfgPath) {
        try {
            Path dir = (cfgPath.getParent() != null) ? cfgPath.getParent() : Paths.get(".");
            WatchService ws = FileSystems.getDefault().newWatchService();
            dir.register(ws,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);

            Executors.newSingleThreadExecutor().submit(() -> {
                for (;;) {
                    WatchKey key = ws.take();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        Path changed = dir.resolve((Path) ev.context());
                        if (changed.getFileName().equals(cfgPath.getFileName())) {
                            System.out.println("[notifier] Detected config change: " + cfgPath +
                                    " (restart the app to reload)");
                        }
                    }
                    key.reset();
                }
            });
        } catch (Exception e) {
            System.err.println("[watch] " + e.getMessage());
        }
    }

}
