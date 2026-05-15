package net.dark.threecore.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public final class TrapMythicMechanics implements Listener {
    @EventHandler
    public void onMythicMechanicLoad(MythicMechanicLoadEvent event) {
        if (!event.getMechanicName().equalsIgnoreCase("trapteleport")) return;
        event.register(new TrapTeleportMechanic(event.getConfig()));
    }

    private static final class TrapTeleportMechanic implements ITargetedEntitySkill {
        private final double x;
        private final double y;
        private final double z;
        private final boolean relative;
        private final boolean local;
        private final boolean snap;
        private final boolean snapY;

        private TrapTeleportMechanic(MythicLineConfig config) {
            this.x = config.getDouble(new String[]{"x", "dx"}, 0.0);
            this.y = config.getDouble(new String[]{"y", "dy"}, 0.0);
            this.z = config.getDouble(new String[]{"z", "dz"}, 0.0);
            this.relative = config.getBoolean(new String[]{"relative", "r"}, false);
            this.local = config.getBoolean(new String[]{"local", "l"}, false);
            this.snap = config.getBoolean(new String[]{"snap", "grid"}, false);
            this.snapY = config.getBoolean(new String[]{"snapy", "gridy"}, false);
        }

        @Override
        public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
            if (target == null || target.getBukkitEntity() == null) return SkillResult.INVALID_TARGET;
            org.bukkit.entity.Entity entity = target.getBukkitEntity();
            Location location = entity.getLocation();
            if (snap) {
                location.setX(Math.floor(location.getX()) + 0.5);
                location.setZ(Math.floor(location.getZ()) + 0.5);
                if (snapY) location.setY(Math.floor(location.getY()));
            }
            if (relative) {
                Vector offset = local ? localOffset(location, x, y, z) : new Vector(x, y, z);
                location.add(offset);
            } else if (!snap) {
                location.add(x, y, z);
            }
            entity.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
            return SkillResult.SUCCESS;
        }

        private static Vector localOffset(Location location, double x, double y, double z) {
            Vector forward = location.getDirection().clone().normalize();
            if (!Double.isFinite(forward.getX()) || !Double.isFinite(forward.getY()) || !Double.isFinite(forward.getZ())) {
                forward = new Vector(0, 0, 1);
            }
            Vector up = new Vector(0, 1, 0);
            Vector right = forward.clone().crossProduct(up).normalize();
            if (!Double.isFinite(right.getX()) || !Double.isFinite(right.getY()) || !Double.isFinite(right.getZ())) {
                right = new Vector(1, 0, 0);
            }
            return right.multiply(x).add(up.multiply(y)).add(forward.multiply(z));
        }
    }
}
