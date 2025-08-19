package com.example;

import java.io.IOException;
import java.util.*;

final class NotifierBackend {

    void notify(String title, String content, String iconUrlOrPath, String link) throws IOException, InterruptedException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            // PowerShell toast (Windows için)
            String safeTitle = escapeForPS(title == null ? "" : title);
            String safeBody  = escapeForPS(content == null ? "" : content);
            String safeIcon  = escapeForPS(iconUrlOrPath == null ? "" : iconUrlOrPath);

            String ps = "powershell.exe -NoProfile -Command \""
                    + "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; "
                    + "$t = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastImageAndText02); "
                    + "$xml = $t; "
                    + "$xml.GetElementsByTagName('text').Item(0).AppendChild($xml.CreateTextNode('" + safeTitle + "')) | Out-Null; "
                    + "$xml.GetElementsByTagName('text').Item(1).AppendChild($xml.CreateTextNode('" + safeBody + "')) | Out-Null; "
                    + "if ('" + safeIcon + "' -ne '') { $xml.GetElementsByTagName('image').Item(0).SetAttribute('src','" + safeIcon + "'); } "
                    + "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml); "
                    + "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('JavaNotifier').Show($toast)\"";

            new ProcessBuilder("cmd.exe", "/c", ps).inheritIO().start().waitFor();

        } else if (os.contains("linux")) {
            // ubuntuda test etmek için
            List<String> cmd = new ArrayList<>(List.of("notify-send",
                    title == null ? "" : title, content == null ? "" : content));
            if (iconUrlOrPath != null && !iconUrlOrPath.isBlank()) {
                cmd.add("-i");
                cmd.add(iconUrlOrPath);
            }
            new ProcessBuilder(cmd).inheritIO().start().waitFor();

        } else {
            System.out.println("[NOTIFY] " + title + " - " + content);
        }
    }

    private String escapeForPS(String s) {
        return s.replace("'", "''").replace("\"", "`\"");
    }
}
