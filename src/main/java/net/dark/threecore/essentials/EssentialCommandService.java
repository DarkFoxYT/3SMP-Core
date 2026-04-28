package net.dark.threecore.essentials;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class EssentialCommandService {
    private static final String BASE_PERMISSION = "3smpcore.essentials";
    private static final String SPEED_PERMISSION = "3smpcore.command.speed";
    private static final String FLY_PERMISSION = "3smpcore.command.fly";
    private static final String GAMEMODE_PERMISSION = "3smpcore.command.gamemode";
    private static final String GAMEMODE_OTHERS_PERMISSION = "3smpcore.command.gamemode.others";
    private static final String TIME_PERMISSION = "3smpcore.command.time";
    private static final String GAMERULE_PERMISSION = "3smpcore.command.gamerule";

    public void speed(CommandContext context) {
        if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; }
        if (!allowed(player, SPEED_PERMISSION)) return;
        if (context.args().length == 0) { Text.send(player, "<red>Usage: /speed <0-10|reset></red>"); return; }
        if (context.arg(0).equalsIgnoreCase("reset")) {
            if (player.isFlying()) player.setFlySpeed(0.1f);
            else player.setWalkSpeed(0.2f);
            Text.send(player, "<green>Speed reset.</green>");
            return;
        }
        float speed = Math.max(0f, Math.min(10f, parseFloat(context.arg(0), 1f))) / 10f;
        if (player.isFlying()) player.setFlySpeed(speed); else player.setWalkSpeed(speed == 0f ? 0f : Math.max(0.01f, speed));
        Text.send(player, "<green>Speed set to</green> <white>" + context.arg(0) + "</white><gray>/10.</gray>");
    }

    public void fly(CommandContext context) {
        if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; }
        if (!allowed(player, FLY_PERMISSION)) return;
        boolean enabled = context.args().length == 0 ? !player.getAllowFlight() : Boolean.parseBoolean(context.arg(0));
        player.setAllowFlight(enabled);
        if (!enabled) player.setFlying(false);
        Text.send(player, enabled ? "<green>Flight enabled.</green>" : "<yellow>Flight disabled.</yellow>");
    }

    public void gamemode(CommandContext context) {
        if (!context.sender().hasPermission(BASE_PERMISSION) && !context.sender().hasPermission(GAMEMODE_PERMISSION)) { Text.send(context.sender(), "<red>No permission.</red>"); return; }
        if (context.args().length == 0) { Text.send(context.sender(), "<red>Usage: /gamemode <survival|creative|adventure|spectator> [player]</red>"); return; }
        GameMode mode = parseGameMode(context.arg(0));
        if (mode == null) { Text.send(context.sender(), "<red>Unknown gamemode.</red>"); return; }
        boolean targetOther = context.args().length >= 2;
        if (targetOther && !context.sender().hasPermission(BASE_PERMISSION) && !context.sender().hasPermission(GAMEMODE_OTHERS_PERMISSION)) {
            Text.send(context.sender(), "<red>No permission.</red>");
            return;
        }
        Player target = context.args().length >= 2 ? Bukkit.getPlayerExact(context.arg(1)) : context.sender() instanceof Player p ? p : null;
        if (target == null) { Text.send(context.sender(), "<red>Player not found.</red>"); return; }
        if (!targetOther && target instanceof Player player && !allowed(player, GAMEMODE_PERMISSION)) return;
        target.setGameMode(mode);
        Text.send(context.sender(), "<green>Set gamemode for</green> <white>" + target.getName() + "</white><gray> to </gray><white>" + mode.name().toLowerCase(Locale.ROOT) + "</white><gray>.</gray>");
    }

    public void shortcut(CommandContext context, GameMode mode) {
        if (!(context.sender() instanceof Player player)) { Text.send(context.sender(), "<red>Players only.</red>"); return; }
        if (!allowed(player, GAMEMODE_PERMISSION)) return;
        player.setGameMode(mode);
        Text.send(player, "<green>Gamemode set to</green> <white>" + mode.name().toLowerCase(Locale.ROOT) + "</white><gray>.</gray>");
    }

    public void time(CommandContext context) {
        if (!context.sender().hasPermission(BASE_PERMISSION) && !context.sender().hasPermission(TIME_PERMISSION)) { Text.send(context.sender(), "<red>No permission.</red>"); return; }
        if (context.args().length == 0) { Text.send(context.sender(), "<red>Usage: /time <day|noon|night|midnight|ticks> [world]</red>"); return; }
        World world = context.args().length >= 2 ? Bukkit.getWorld(context.arg(1)) : context.sender() instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        if (world == null) { Text.send(context.sender(), "<red>World not found.</red>"); return; }
        long time = switch (context.arg(0).toLowerCase(Locale.ROOT)) {
            case "day" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            default -> parseLong(context.arg(0), 1000L);
        };
        world.setTime(time);
        Text.send(context.sender(), "<green>Time set in</green> <white>" + world.getName() + "</white><gray>.</gray>");
    }

    public void gamerule(CommandContext context) {
        if (!context.sender().hasPermission(BASE_PERMISSION) && !context.sender().hasPermission(GAMERULE_PERMISSION)) { Text.send(context.sender(), "<red>No permission.</red>"); return; }
        if (context.args().length < 2) { Text.send(context.sender(), "<red>Usage: /gamerule <rule> <value> [world]</red>"); return; }
        World world = context.args().length >= 3 ? Bukkit.getWorld(context.arg(2)) : context.sender() instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
        if (world == null) { Text.send(context.sender(), "<red>World not found.</red>"); return; }
        boolean ok = applyGameRule(world, context.arg(0), context.arg(1));
        Text.send(context.sender(), ok ? "<green>Gamerule updated.</green>" : "<red>Invalid gamerule or value.</red>");
    }

    public List<String> completeGameMode(String[] args) {
        if (args.length <= 1) return List.of("survival", "creative", "adventure", "spectator");
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    public List<String> completeTime(String[] args) {
        if (args.length <= 1) return List.of("day", "noon", "night", "midnight");
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    public List<String> completeGamerule(String[] args) {
        if (args.length <= 1) return availableGameRules().toList();
        if (args.length == 2) return List.of("true", "false");
        return Bukkit.getWorlds().stream().map(World::getName).toList();
    }

    private boolean applyGameRule(World world, String ruleName, String rawValue) {
        GameRule<?> rawRule = Registry.GAME_RULE.get(NamespacedKey.minecraft(toSnakeCase(ruleName)));
        if (rawRule == null) return false;
        return setTypedRule(world, rawRule, rawValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean setTypedRule(World world, GameRule rawRule, String rawValue) {
        try {
            Class<?> type = rawRule.getType();
            if (type == Boolean.class) return world.setGameRule(rawRule, Boolean.parseBoolean(rawValue));
            if (type == Integer.class) return world.setGameRule(rawRule, Integer.parseInt(rawValue));
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Stream<String> availableGameRules() {
        return Registry.GAME_RULE.stream().map(rule -> rule.key().value());
    }

    private String toSnakeCase(String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private boolean allowed(Player player, String permission) {
        if (DuelService.isDuelPlayer(player)) {
            Text.send(player, "<red>You cannot use that command during a duel.</red>");
            return false;
        }
        if (player.hasPermission(BASE_PERMISSION) || player.hasPermission(permission)) return true;
        Text.send(player, "<red>No permission.</red>");
        return false;
    }
    private GameMode parseGameMode(String input) { return switch (input.toLowerCase(Locale.ROOT)) { case "0", "s", "survival" -> GameMode.SURVIVAL; case "1", "c", "creative" -> GameMode.CREATIVE; case "2", "a", "adventure" -> GameMode.ADVENTURE; case "3", "sp", "spectator" -> GameMode.SPECTATOR; default -> null; }; }
    private float parseFloat(String input, float fallback) { try { return Float.parseFloat(input); } catch (Exception ignored) { return fallback; } }
    private long parseLong(String input, long fallback) { try { return Long.parseLong(input); } catch (Exception ignored) { return fallback; } }
}
