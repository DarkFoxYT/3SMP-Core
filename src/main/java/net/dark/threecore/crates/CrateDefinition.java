package net.dark.threecore.crates;

import org.bukkit.Material;

import java.util.List;

record CrateDefinition(
        String id,
        String displayName,
        String keyItem,
        String blockItem,
        String modelId,
        Material fallbackBlock,
        double hologramYOffset,
        boolean protectBlock,
        List<String> description,
        List<String> hologram,
        List<CrateReward> rewards
) {
}
