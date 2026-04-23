package net.dark.threecore.social;

import net.dark.threecore.command.base.CommandContext;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class FriendService implements Listener {
    private final JavaPlugin plugin;
    private final PlayerDataRepository repository;
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    public FriendService(JavaPlugin plugin, PlayerDataRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    public void handle(CommandContext context) {
        if (!(context.sender() instanceof Player player)) {
            Text.send(context.sender(), "<red>Players only.</red>");
            return;
        }
        String sub = context.arg(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "", "list" -> showList(player);
            case "add", "request" -> request(player, context.arg(1));
            case "accept" -> accept(player, context.arg(1));
            case "deny" -> deny(player, context.arg(1));
            case "remove", "delete" -> remove(player, context.arg(1));
            default -> Text.send(player, "<gray>/friend list|add <player>|accept <player>|deny <player>|remove <player></gray>");
        }
    }

    public List<String> complete(CommandContext context) {
        if (context.args().length <= 1) {
            return List.of("list", "add", "accept", "deny", "remove");
        }
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public void request(Player sender, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            Text.send(sender, "<red>Usage: /friend add <player></red>");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Text.send(sender, "<red>Player not found.</red>");
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            Text.send(sender, "<red>You cannot friend yourself.</red>");
            return;
        }
        if (isFriend(sender.getUniqueId(), target.getUniqueId())) {
            Text.send(sender, "<yellow>You are already friends.</yellow>");
            return;
        }
        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());
        Text.send(sender, "<green>Friend request sent to " + target.getName() + ".</green>");
        sendRequestMessage(target, sender.getName());
    }

    public void accept(Player player, String requesterName) {
        UUID requester = resolveRequest(player, requesterName);
        if (requester == null) {
            Text.send(player, "<red>No matching friend request.</red>");
            return;
        }
        pendingRequests.remove(player.getUniqueId());
        repository.addFriend(player.getUniqueId(), requester);
        repository.addFriend(requester, player.getUniqueId());
        Text.send(player, "<green>Friend request accepted.</green>");
        Player requesterPlayer = Bukkit.getPlayer(requester);
        if (requesterPlayer != null) Text.send(requesterPlayer, "<green>" + player.getName() + " accepted your friend request.</green>");
    }

    public void deny(Player player, String requesterName) {
        UUID requester = resolveRequest(player, requesterName);
        if (requester == null) {
            Text.send(player, "<red>No matching friend request.</red>");
            return;
        }
        pendingRequests.remove(player.getUniqueId());
        Text.send(player, "<gray>Friend request denied.</gray>");
        Player requesterPlayer = Bukkit.getPlayer(requester);
        if (requesterPlayer != null) Text.send(requesterPlayer, "<gray>" + player.getName() + " denied your friend request.</gray>");
    }

    public void remove(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            Text.send(player, "<red>Usage: /friend remove <player></red>");
            return;
        }
        UUID target = resolveByName(targetName);
        if (target == null) {
            Text.send(player, "<red>Player not found.</red>");
            return;
        }
        repository.removeFriend(player.getUniqueId(), target);
        repository.removeFriend(target, player.getUniqueId());
        Text.send(player, "<gray>Friend removed.</gray>");
    }

    public boolean isFriend(UUID left, UUID right) {
        return repository.friends(left).contains(right);
    }

    public Set<UUID> friends(UUID owner) {
        return repository.friends(owner);
    }

    public String friendList(UUID owner) {
        Set<UUID> friends = friends(owner);
        if (friends.isEmpty()) return "No friends";
        return friends.stream().map(this::nameOf).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.joining(", "));
    }

    public List<Player> sortFriendsFirst(Player owner, List<? extends Player> candidates) {
        Set<UUID> friends = friends(owner.getUniqueId());
        return new java.util.ArrayList<>(candidates).stream().map(player -> (Player) player)
                .sorted(Comparator
                        .comparing((Player target) -> !friends.contains(target.getUniqueId()))
                        .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public int count(UUID owner) {
        return friends(owner).size();
    }

    private void showList(Player player) {
        String list = friendList(player.getUniqueId());
        Text.send(player, "<gradient:#1A2A4A:#D6E8F7>Friends</gradient> <gray>" + list + "</gray>");
    }

    private void sendRequestMessage(Player target, String senderName) {
        Component base = Text.mm("<gradient:#1A2A4A:#D6E8F7>Friend Request</gradient> <gray>from</gray> <white>" + senderName + "</white>");
        Component accept = Text.mm(" <green>[ACCEPT]</green>")
                .clickEvent(ClickEvent.runCommand("/friend accept " + senderName))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>Accept " + senderName + " as a friend.</gray>")));
        Component deny = Text.mm(" <red>[DENY]</red>")
                .clickEvent(ClickEvent.runCommand("/friend deny " + senderName))
                .hoverEvent(HoverEvent.showText(Text.mm("<gray>Deny this friend request.</gray>")));
        target.sendMessage(base.append(accept).append(deny));
    }

    private UUID resolveRequest(Player player, String requesterName) {
        UUID requester = pendingRequests.get(player.getUniqueId());
        if (requester == null) return null;
        if (requesterName == null || requesterName.isBlank()) return requester;
        String name = nameOf(requester);
        return name.equalsIgnoreCase(requesterName) ? requester : null;
    }

    private UUID resolveByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online.getUniqueId() : null;
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString() : offline.getName();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingRequests.remove(event.getPlayer().getUniqueId());
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().equals(event.getPlayer().getUniqueId()));
    }
}


