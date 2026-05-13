package net.dark.threecore.dungeons.boulder;

import java.util.UUID;

public final class BoulderTrapAPI {
    private static BoulderTrapService service;

    private BoulderTrapAPI() {
    }

    public static void install(BoulderTrapService installed) {
        service = installed;
    }

    public static int activeCount() {
        return service == null ? 0 : service.activeCount();
    }

    public static void activate(BoulderTrapService.Instance instance) {
        if (service != null) service.activate(instance);
    }

    public static void removeSession(UUID sessionId) {
        if (service != null) service.removeSession(sessionId);
    }

    public static void killAll() {
        if (service != null) service.killAll();
    }
}
