package net.dark.threecore.dungeons.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModelEngineHook {
    private final JavaPlugin plugin;
    private boolean warned;

    public ModelEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        org.bukkit.plugin.Plugin modelEngine = Bukkit.getPluginManager().getPlugin("ModelEngine");
        return modelEngine != null && modelEngine.isEnabled();
    }

    public boolean hasModel(String id) {
        return isEnabled() && id != null && !id.isBlank();
    }

    public boolean attachModel(Entity entity, String modelId) {
        if (entity == null || !hasModel(modelId)) return false;
        warnOnce("ModelEngine is installed, but this build uses safe fallback rendering unless the ModelEngine API adapter is added.");
        return false;
    }

    public boolean playAnimation(Entity entity, String animation, boolean loop) {
        if (entity == null || animation == null || animation.isBlank() || !isEnabled()) return false;
        warnOnce("ModelEngine animation adapter unavailable; boulder will use BlockDisplay fallback.");
        return false;
    }

    public boolean stopAnimation(Entity entity, String animation) {
        if (entity == null || animation == null || animation.isBlank() || !isEnabled()) return false;
        return false;
    }

    public void removeModel(Entity entity) {
    }

    public void setModelRotation(Entity entity, float yaw, float pitch) {
        if (entity != null) entity.setRotation(yaw, pitch);
    }

    public void setModelScale(Entity entity, double scale) {
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        plugin.getLogger().info(message);
    }
}
