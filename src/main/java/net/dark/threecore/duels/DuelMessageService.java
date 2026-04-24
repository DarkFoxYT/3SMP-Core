package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;

public final class DuelMessageService {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;

    public DuelMessageService(JavaPlugin plugin, ConfigFiles configs) {
        this.plugin = plugin;
        this.configs = configs;
    }

    public void queue(Player player, String mode, String kit) {
        actionBar(player, message("queue", "<gradient:#60a5fa:#c084fc>Queued</gradient> <gray>" + mode + "</gray> <white>" + kit + "</white>"));
    }

    public void found(Collection<UUID> members) {
        for (UUID uuid : members) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
            actionBar(player, message("found", "<yellow>Match found. Teleporting...</yellow>"));
        }
    }

    public void countdown(Collection<UUID> members, int seconds) {
        String title = message("countdown.title", "<gradient:#60a5fa:#c084fc>" + seconds + "</gradient>");
        String subtitle = message("countdown.subtitle", "<gray>Prepare yourself.</gray>");
        title(members, title, subtitle);
        actionBar(members, message("countdown.actionbar", "<gray>Starting in</gray> <white>" + seconds + "</white>"));
    }

    public void start(Collection<UUID> members) {
        title(members, message("start.title", "<gradient:#34d399:#22c55e>START!</gradient>"), message("start.subtitle", "<gray>Fight!</gray>"));
        actionBar(members, message("start.actionbar", "<green>Fight!</green>"));
    }

    public void roundEnd(Collection<UUID> members, int scoreA, int scoreB) {
        actionBar(members, message("round-end", "<gradient:#60a5fa:#c084fc>Round complete.</gradient> <gray>Score:</gray> <white>" + scoreA + "-" + scoreB + "</white>"));
    }

    public void summary(Collection<UUID> members, String winner, String duration, int kills, int deaths, int wins, int losses, int streak) {
        String title = message("summary.title", "<gradient:#60a5fa:#c084fc>Duel Over</gradient>");
        String subtitle = message("summary.subtitle", "<white>" + winner + "</white>");
        title(members, title, subtitle);
        for (UUID uuid : members) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) continue;
            Text.send(player, message("summary.line1", "<gray>Duration:</gray> <white>" + duration + "</white>"));
            Text.send(player, message("summary.line2", "<gray>Kills:</gray> <white>" + kills + "</white> <gray>Deaths:</gray> <white>" + deaths + "</white>"));
            Text.send(player, message("summary.line3", "<gray>Wins:</gray> <white>" + wins + "</white> <gray>Losses:</gray> <white>" + losses + "</white>"));
            Text.send(player, message("summary.line4", "<gray>Streak:</gray> <white>" + streak + "</white>"));
        }
    }

    private void actionBar(Player player, String message) {
        player.sendActionBar(Text.mm(message));
    }

    private void actionBar(Collection<UUID> members, String message) {
        for (UUID uuid : members) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) player.sendActionBar(Text.mm(message));
        }
    }

    private void title(Collection<UUID> members, String title, String subtitle) {
        Title.Times times = Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(900), Duration.ofMillis(250));
        for (UUID uuid : members) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) player.showTitle(Title.title(Text.mm(title), Text.mm(subtitle), times));
        }
    }

    private String message(String path, String fallback) {
        return configs.get("duels/messages.yml").getString(path, fallback);
    }
}
