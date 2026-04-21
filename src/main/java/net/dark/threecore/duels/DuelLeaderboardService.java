package net.dark.threecore.duels;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.data.PlayerDataRepository;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.model.PlayerProgressionData;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DuelLeaderboardService {
    private final PlayerDataRepository repository;
    private final MenuService menuService;
    private final ConfigFiles configs;

    public DuelLeaderboardService(PlayerDataRepository repository, MenuService menuService, ConfigFiles configs) {
        this.repository = repository;
        this.menuService = menuService;
        this.configs = configs;
    }

    public void open(Player player) {
        menuService.open(player, build());
    }

    public Inventory build() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_LEADERBOARD, "leaderboard"), 27, "3SMP Duel Leaderboard");
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        List<UUID> top = Arrays.stream(Bukkit.getOfflinePlayers())
                .map(p -> p.getUniqueId())
                .sorted(Comparator.comparingInt((UUID id) -> repository.load(id).duelRating()).reversed())
                .limit(5)
                .toList();
        int slot = 10;
        int place = 1;
        for (UUID uuid : top) {
            PlayerProgressionData data = repository.load(uuid);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            inv.setItem(slot++, button(Material.PLAYER_HEAD, "<gradient:#60a5fa:#c084fc>#" + place + " " + (name == null ? uuid.toString().substring(0, 8) : name) + "</gradient>", List.of("<gray>Rating: " + data.duelRating() + "</gray>", "<gray>Wins: " + data.duelWins() + " | Losses: " + data.duelLosses() + "</gray>", "<gray>Streak: " + data.duelWinStreak() + " | Best: " + data.duelBestWinStreak() + "</gray>", "<gray>Prize: " + prize(place) + "</gray>")));
            place++;
        }
        inv.setItem(22, button(Material.SUNFLOWER, "<gradient:#f59e0b:#fbbf24>Monthly Prizes</gradient>", List.of(
                "<gray>1st:</gray> <white>" + prize(1) + "</white>",
                "<gray>2nd:</gray> <white>" + prize(2) + "</white>",
                "<gray>3rd:</gray> <white>" + prize(3) + "</white>",
                "<dark_gray>Monthly reset is manual.</dark_gray>"
        )));
        return inv;
    }

    public void handleClick(Player player, int slot) {
        Text.send(player, "<gray>Leaderboard is view-only for now.</gray>");
    }

    private String prize(int place) {
        return configs.get("duels.yml").getString("duels.leaderboard.prizes." + place + ".display", place <= 3 ? "Configured manually" : "None");
    }

    private void fill(Inventory inv, Material material) { for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, button(material, " ", List.of())); }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
