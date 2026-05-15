package net.dark.threecore.crates;

import org.bukkit.Material;

import java.util.List;

record CrateReward(
        String id,
        String tier,
        double chance,
        String display,
        Material material,
        int amount,
        String itemsAdderId,
        boolean giveItem,
        List<String> commands,
        List<String> lore
) {
}
