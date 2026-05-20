package net.dark.threecore.essentials;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.duels.DuelService;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TpaService {
    private static final long REQUEST_TIMEOUT_MILLIS = 60_000L;
    private final Map<UUID, TpaRequest> requestsByTarget = new HashMap<>();

    public void request(CommandContext context) {
        if (!(context.sender() instanceof Player requester)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        if (!allowed(requester)) return;
        if (context.args().length == 0) {
            Text.send(requester, "<yellow>Use /tpa <player>.</yellow>");
            return;
        }
        Player target = Bukkit.getPlayerExact(context.arg(0));
        if (target == null) {
            Text.send(requester, "<red>Player not found.</red>");
            return;
        }
        if (target.equals(requester)) {
            Text.send(requester, "<red>You cannot TPA to yourself.</red>");
            return;
        }
        if (!allowed(target)) {
            Text.send(requester, "<red>That player cannot receive TPA requests right now.</red>");
            return;
        }
        requestsByTarget.put(target.getUniqueId(), new TpaRequest(requester.getUniqueId(), target.getUniqueId(), System.currentTimeMillis()));
        Text.send(requester, "<green>TPA request sent to</green> <white>" + target.getName() + "</white><gray>.</gray>");
        Text.send(target, "<gradient:#60a5fa:#c084fc>TPA request</gradient> <gray>from</gray> <white>" + requester.getName() + "</white><gray>. Use <white>/tpaccept</white> or <white>/tpdeny</white>.</gray>");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7F, 1.35F);
    }

    public void accept(CommandContext context) {
        if (!(context.sender() instanceof Player target)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        TpaRequest request = pending(target);
        if (request == null) {
            Text.send(target, "<yellow>You have no pending TPA request.</yellow>");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        requestsByTarget.remove(target.getUniqueId());
        if (requester == null || !requester.isOnline()) {
            Text.send(target, "<red>That player is no longer online.</red>");
            return;
        }
        if (!allowed(target) || !allowed(requester)) return;
        requester.teleport(target.getLocation());
        requester.playSound(requester.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8F, 1.1F);
        Text.send(requester, "<green>Teleported to</green> <white>" + target.getName() + "</white><gray>.</gray>");
        Text.send(target, "<green>Accepted TPA from</green> <white>" + requester.getName() + "</white><gray>.</gray>");
    }

    public void deny(CommandContext context) {
        if (!(context.sender() instanceof Player target)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        TpaRequest request = pending(target);
        if (request == null) {
            Text.send(target, "<yellow>You have no pending TPA request.</yellow>");
            return;
        }
        requestsByTarget.remove(target.getUniqueId());
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester != null) Text.send(requester, "<red>Your TPA request to " + target.getName() + " was denied.</red>");
        Text.send(target, "<gray>TPA request denied.</gray>");
    }

    public List<String> complete(String[] args) {
        if (args.length <= 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }

    private TpaRequest pending(Player target) {
        TpaRequest request = requestsByTarget.get(target.getUniqueId());
        if (request == null) return null;
        if (System.currentTimeMillis() - request.createdAt() <= REQUEST_TIMEOUT_MILLIS) return request;
        requestsByTarget.remove(target.getUniqueId());
        return null;
    }

    private boolean allowed(Player player) {
        if (!player.hasPermission("3smpcore.tpa.use")) {
            Text.send(player, "<red>No permission.</red>");
            return false;
        }
        if (DuelService.isDuelPlayer(player)) {
            Text.send(player, "<red>You cannot use TPA during a duel.</red>");
            return false;
        }
        String world = player.getWorld() == null ? "" : player.getWorld().getName().toLowerCase(Locale.ROOT);
        if (world.contains("_arena_") || world.contains("_match_")) {
            Text.send(player, "<red>You cannot use TPA in duel worlds.</red>");
            return false;
        }
        return true;
    }

    private record TpaRequest(UUID requester, UUID target, long createdAt) {}
}
