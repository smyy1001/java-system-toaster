package com.example;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class App {
    public static void main(String[] args) throws Exception {
        // servisleri yükle
        Path cfgPath = ConfigLoader.resolveConfigPath(System.getProperty("config", "services.json"));
        RootConfig cfg = ConfigLoader.loadConfig(cfgPath);

        // last-seen ids
        Map<String, String> state = StateStore.loadState(ConfigLoader.expandPath(cfg.stateFile));

        NotifierBackend backend = new NotifierBackend();
        ScheduledExecutorService exec =
                Executors.newScheduledThreadPool(Math.max(2, cfg.services.size()));

        System.out.println("[notifier] loaded " + cfg.services.size() + " service(s)");
        for (ServiceCfg svc : cfg.services) {
            if (!Boolean.TRUE.equals(svc.enabled)) continue;
            int interval = (svc.pollIntervalSec != null) ? svc.pollIntervalSec : cfg.defaultPollIntervalSec;
            long jitterMs = ThreadLocalRandom.current().nextLong(0, 2000); // smooth starts

            Runnable task = () -> Poller.pollOnce(svc, state, ConfigLoader.expandPath(cfg.stateFile), backend);
            exec.scheduleAtFixedRate(task, jitterMs, interval * 1000L, TimeUnit.MILLISECONDS);

            System.out.println("  • " + svc.name + " every " + interval + "s → " + svc.request.url);
        }

        for (;;) Thread.sleep(60_000);
    }
}
