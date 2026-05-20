package net.dark.threecore.crates;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

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
        Map<String, Integer> enchantments,
        List<String> lore
) {
}
