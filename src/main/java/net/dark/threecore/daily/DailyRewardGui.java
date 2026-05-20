package net.dark.threecore.daily;

import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DailyRewardGui {
    private final DailyRewardManager manager;

    public DailyRewardGui(DailyRewardManager manager) {
        this.manager = manager;
    }

    public Inventory build(Player player) {
        var config = manager.config();
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DAILY_MAIN, "main"), size(config), title(config));
        ItemStack fill = button(config.getString("menu.fill.material", "BLACK_STAINED_GLASS_PANE"), " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);

        DailyRewardState state = manager.state(player.getUniqueId());
        long now = System.currentTimeMillis();
        boolean ready = manager.isClaimReady(state, now);
        int currentDay = manager.currentDay(state);
        int maxDay = Math.max(1, manager.rewardIds().size());

        inv.setItem(config.getInt("menu.slots.streak", 4), button(config.getString("menu.icons.streak.material", "EXPERIENCE_BOTTLE"), "<gradient:#f4cd2a:#eda323:#d28d0d>Current Streak</gradient>", List.of(
                "<gray>Your streak:</gray> <white>" + state.streak() + "</white>",
                "<gray>Total claims:</gray> <white>" + state.totalClaims() + "</white>",
                "<gray>Next reward day:</gray> <white>Day " + currentDay + "</white>"
        )));
        inv.setItem(config.getInt("menu.slots.current", 13), manager.rewardCard(currentDay, true, ready, state));
        inv.setItem(config.getInt("menu.slots.claim", 49), button(ready ? config.getString("menu.icons.claim.material", "LIME_DYE") : config.getString("menu.icons.locked.material", "RED_DYE"),
                ready ? "<green>Claim Reward</green>" : "<red>Claim Locked</red>",
                ready ? List.of("<gray>Click to claim your daily reward now.</gray>") : List.of("<gray>Come back in <white>" + manager.formatRemaining(state, now) + "</white>.</gray>")));

        int rewardSlotIndex = 0;
        for (Integer day : manager.rewardIds()) {
            int slot = manager.rewardSlot(rewardSlotIndex++);
            if (slot < 0 || slot >= inv.getSize()) continue;
            boolean unlocked = day < currentDay || (!ready && day == currentDay);
            inv.setItem(slot, manager.rewardCard(day, unlocked, ready && day == currentDay, state));
        }

        Integer weeklySlot = config.getInt("menu.slots.weekly", 31);
        Integer monthlySlot = config.getInt("menu.slots.monthly", 33);
        if (manager.weeklyEnabled()) inv.setItem(weeklySlot, manager.bonusCard("weekly"));
        if (manager.monthlyEnabled()) inv.setItem(monthlySlot, manager.bonusCard("monthly"));

        inv.setItem(config.getInt("menu.slots.info", 22), button(config.getString("menu.icons.info.material", "BOOK"), "<gradient:#f4cd2a:#eda323:#d28d0d>How It Works</gradient>", List.of(
                "<gray>1 reward every 24 hours.</gray>",
                "<gray>Claim manually from this menu.</gray>",
                "<gray>Rewards scale with your streak.</gray>",
                "<gray>Weekly and monthly bonus rewards are optional.</gray>"
        )));
        inv.setItem(config.getInt("menu.slots.back", 45), button(config.getString("menu.icons.back.material", "ARROW"), "<gray>Back</gray>", List.of("<gray>Return to the previous menu.</gray>")));
        return inv;
    }

    private int size(org.bukkit.configuration.file.YamlConfiguration config) {
        int size = config.getInt("menu.size", 54);
        if (size % 9 != 0) size = 54;
        return Math.max(27, Math.min(54, size));
    }

    private String title(org.bukkit.configuration.file.YamlConfiguration config) {
        return config.getString("menu.title", "3SMP Daily Rewards");
    }

    private ItemStack button(String materialName, String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) material = Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
