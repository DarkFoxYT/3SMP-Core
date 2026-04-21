package net.dark.threecore.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class DungeonMenu {
    public static final String TITLE = ChatColor.GOLD + "Dungeon Selector";
    public static final String LEVELS_TITLE = ChatColor.GOLD + "Dungeon Levels";

    public static final class MenuState {
        public boolean solo = true;
        public int level = 1;
        public DungeonDifficulty difficulty = DungeonDifficulty.EASY;
    }

    private final DungeonManager manager;

    public DungeonMenu(DungeonManager manager) {
        this.manager = manager;
    }

    public Inventory build(Player player) {
        MenuState state = manager.menuState(player);
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);
        fill(inv);
        inv.setItem(10, option("Party", state.solo ? Material.LIGHT_BLUE_WOOL : Material.ORANGE_WOOL, state.solo ? "Solo" : "Team"));
        inv.setItem(11, option("Level", Material.END_CRYSTAL, DungeonLevel.byId(state.level).displayName));
        inv.setItem(12, option("Difficulty", difficultyMaterial(state.difficulty), state.difficulty.name() + " - " + manager.roomsForDifficulty(state.difficulty) + " rooms"));
        inv.setItem(16, option("Start", Material.LIME_WOOL, "Generate dungeon"));
        return inv;
    }

    public Inventory buildLevels(Player player) {
        MenuState state = manager.menuState(player);
        int unlockedLevel = manager.unlockedLevelValue(player);
        Inventory inv = Bukkit.createInventory(null, 27, LEVELS_TITLE);
        fill(inv);
        inv.setItem(10, levelItem(DungeonLevel.JUNGLE, state.level == 1, unlockedLevel >= 1));
        inv.setItem(11, levelItem(DungeonLevel.DESERT, state.level == 2, unlockedLevel >= 2));
        inv.setItem(12, levelItem(DungeonLevel.VOLCANIC, state.level == 3, unlockedLevel >= 3));
        inv.setItem(13, levelItem(DungeonLevel.FROZEN, state.level == 4, unlockedLevel >= 4));
        inv.setItem(14, levelItem(DungeonLevel.ANCIENT, state.level == 5, unlockedLevel >= 5));
        inv.setItem(16, option("Back", Material.BARRIER, "Return to menu"));
        return inv;
    }

    private ItemStack levelItem(DungeonLevel level, boolean selected, boolean unlocked) {
        Material material;
        List<String> lore = new ArrayList<>();
        if (!unlocked) {
            material = Material.RED_STAINED_GLASS_PANE;
            lore.add(ChatColor.RED + "Locked");
        } else if (level.underDev) {
            material = selected ? Material.YELLOW_WOOL : Material.GRAY_WOOL;
            lore.add(ChatColor.GRAY + "Coming soon");
        } else {
            material = selected ? Material.GREEN_WOOL : Material.LIME_WOOL;
            lore.add(ChatColor.GREEN + "Available");
        }
        lore.add(selected ? ChatColor.YELLOW + "Selected" : ChatColor.DARK_GRAY + "Click to select");
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + level.displayName);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Material difficultyMaterial(DungeonDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> Material.WHITE_WOOL;
            case NORMAL -> Material.YELLOW_WOOL;
            case HARD -> Material.ORANGE_WOOL;
            case INSANE -> Material.RED_WOOL;
            case NIGHTMARE -> Material.BLACK_WOOL;
        };
    }

    private ItemStack option(String label, Material material, String line) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + label);
        meta.setLore(List.of(ChatColor.GRAY + line));
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, pane);
            }
        }
    }
}