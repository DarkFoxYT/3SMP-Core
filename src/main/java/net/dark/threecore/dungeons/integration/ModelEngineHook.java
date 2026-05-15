package net.dark.threecore.dungeons.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelEngineHook {
    private final JavaPlugin plugin;
    private final Map<UUID, Object> activeModels = new ConcurrentHashMap<>();
    private boolean warned;

    public ModelEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        org.bukkit.plugin.Plugin modelEngine = Bukkit.getPluginManager().getPlugin("ModelEngine");
        return modelEngine != null && modelEngine.isEnabled();
    }

    public boolean hasModel(String id) {
        if (!isEnabled() || id == null || id.isBlank()) return false;
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object blueprint = api.getMethod("getBlueprint", String.class).invoke(null, id);
            return blueprint != null;
        } catch (Throwable ex) {
            return true;
        }
    }

    public boolean attachModel(Entity entity, String modelId) {
        if (entity == null || !hasModel(modelId)) return false;
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Class<?> activeModelType = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Object modeledEntity = api.getMethod("getOrCreateModeledEntity", Entity.class).invoke(null, entity);
            Object activeModel = api.getMethod("createActiveModel", String.class).invoke(null, modelId);
            Method addModel = modeledEntity.getClass().getMethod("addModel", activeModelType, boolean.class);
            addModel.invoke(modeledEntity, activeModel, true);
            try {
                modeledEntity.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(modeledEntity, false);
            } catch (ReflectiveOperationException ignored) {
            }
            activeModels.put(entity.getUniqueId(), activeModel);
            return true;
        } catch (Throwable ex) {
            warnOnce("ModelEngine model attach failed; using BlockDisplay fallback. " + ex.getMessage());
            return false;
        }
    }

    public boolean playAnimation(Entity entity, String animation, boolean loop) {
        if (entity == null || animation == null || animation.isBlank() || !isEnabled()) return false;
        Object activeModel = activeModels.get(entity.getUniqueId());
        if (activeModel == null) return false;
        try {
            Object handler = activeModel.getClass().getMethod("getAnimationHandler").invoke(activeModel);
            handler.getClass().getMethod("playAnimation", String.class, double.class, double.class, double.class, boolean.class)
                    .invoke(handler, animation, 0.0D, 0.0D, 1.0D, loop);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    public boolean stopAnimation(Entity entity, String animation) {
        if (entity == null || animation == null || animation.isBlank() || !isEnabled()) return false;
        Object activeModel = activeModels.get(entity.getUniqueId());
        if (activeModel == null) return false;
        try {
            Object handler = activeModel.getClass().getMethod("getAnimationHandler").invoke(activeModel);
            handler.getClass().getMethod("stopAnimation", String.class).invoke(handler, animation);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    public void removeModel(Entity entity) {
        if (entity == null || !isEnabled()) return;
        activeModels.remove(entity.getUniqueId());
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            api.getMethod("removeModeledEntity", Entity.class).invoke(null, entity);
        } catch (Throwable ignored) {
        }
    }

    public void setModelRotation(Entity entity, float yaw, float pitch) {
        if (entity != null) entity.setRotation(yaw, pitch);
    }

    public void setModelScale(Entity entity, double scale) {
        if (entity == null) return;
        Object activeModel = activeModels.get(entity.getUniqueId());
        if (activeModel == null) return;
        try {
            activeModel.getClass().getMethod("setScale", double.class).invoke(activeModel, scale);
        } catch (Throwable ignored) {
        }
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        plugin.getLogger().info(message);
    }
}
