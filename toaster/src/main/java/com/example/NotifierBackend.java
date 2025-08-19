package com.example;

import java.io.IOException;
import java.util.*;
final class NotifierBackend {

    void notify(String title, String content, String iconUrlOrPath, String link)
            throws IOException, InterruptedException {

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            // --- WINDOWS TOAST with clickable button (opens link) ---
            String safeTitle = escapeForPS(title == null ? "" : title);
            String safeBody  = escapeForPS(content == null ? "" : content);
            String safeIcon  = escapeForPS(iconUrlOrPath == null ? "" : iconUrlOrPath);
            String safeLink  = escapeForPS(link == null ? "" : link);

            String ps = "powershell.exe -NoProfile -Command \""
                    + "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; "
                    + "$xml = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastImageAndText02); "
                    + "$xml.GetElementsByTagName('text').Item(0).AppendChild($xml.CreateTextNode('" + safeTitle + "')) | Out-Null; "
                    + "$xml.GetElementsByTagName('text').Item(1).AppendChild($xml.CreateTextNode('" + safeBody + "')) | Out-Null; "
                    + "if ('" + safeIcon + "' -ne '') { $xml.GetElementsByTagName('image').Item(0).SetAttribute('src','" + safeIcon + "'); } "
                    // add a clickable button if link present
                    + "if ('" + safeLink + "' -ne '') { "
                    + "  $actions = $xml.CreateElement('actions'); "
                    + "  $action  = $xml.CreateElement('action'); "
                    + "  $action.SetAttribute('content','Aç'); "
                    + "  $action.SetAttribute('arguments','" + safeLink + "'); "
                    + "  $action.SetAttribute('activationType','protocol'); "
                    + "  $actions.AppendChild($action) | Out-Null; "
                    + "  $xml.DocumentElement.AppendChild($actions) | Out-Null; "
                    + "} "
                    + "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml); "
                    + "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JavaNotifier').Show($toast)\"";

            new ProcessBuilder("cmd.exe", "/c", ps).inheritIO().start().waitFor();

        } else if (os.contains("linux")) {
            // Linux: notify-send (buton desteği çoğu masaüstünde sınırlı)
            var cmd = new java.util.ArrayList<>(java.util.List.of(
                    "notify-send",
                    title == null ? "" : title,
                    (content == null ? "" : content) + (link != null ? "\n" + link : "")
            ));
            if (iconUrlOrPath != null && !iconUrlOrPath.isBlank()) {
                cmd.add("-i"); cmd.add(iconUrlOrPath);
            }
            new ProcessBuilder(cmd).inheritIO().start().waitFor();

        } else {
            System.out.println("[NOTIFY] " + title + " - " + content + (link != null ? " (" + link + ")" : ""));
        }
    }

    private String escapeForPS(String s) {
        return s.replace("'", "''").replace("\"", "`\"");
    }
}
