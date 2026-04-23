package net.dark.threecore.launchpads;

import org.bukkit.block.Sign;
import org.bukkit.event.block.SignChangeEvent;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gui.MenuService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.gui.menu.CoreMenuHolder;
import net.dark.threecore.gui.menu.CoreMenuType;
import net.dark.threecore.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LaunchpadService implements Listener {
    private static final String ITEM_KEY = "3smpcore_launchpad_item";

    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final MenuService menuService;
    private final PerkService perkService;
    private final Map<String, LaunchpadDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, PlacedLaunchpad> placedPads = new LinkedHashMap<>();
    private final Map<java.util.UUID, PendingSignEdit> pendingTargetEdits = new java.util.HashMap<>();
    private final Map<java.util.UUID, PendingRenameEdit> pendingRenameEdits = new java.util.HashMap<>();

    public LaunchpadService(JavaPlugin plugin, ConfigFiles configs, MenuService menuService, PerkService perkService) {
        this.plugin = plugin;
        this.configs = configs;
        this.menuService = menuService;
        this.perkService = perkService;
        reload();
    }

    public void reload() {
        definitions.clear();
        placedPads.clear();

        var config = configs.get("core/config.yml");
        ConfigurationSection launchpads = config.getConfigurationSection("launchpads");
        if (launchpads != null) {
            for (String id : launchpads.getKeys(false)) {
                ConfigurationSection pad = launchpads.getConfigurationSection(id);
                if (pad == null) continue;
                definitions.put(id.toLowerCase(Locale.ROOT), new LaunchpadDefinition(
                        id.toLowerCase(Locale.ROOT),
                        pad.getString("display-name", id),
                        parseMaterial(pad.getString("material", "SLIME_BLOCK")),
                        pad.getBoolean("enabled", true),
                        pad.getString("mode", "DIRECTION"),
                        new Vector(pad.getDouble("velocity.x", 0.0), pad.getDouble("velocity.y", 0.75), pad.getDouble("velocity.z", 0.0)),
                        pad.getDouble("strength", 1.0),
                        readLocation(pad.getConfigurationSection("target")),
                        pad.getString("facing", "NORTH")
                ));
            }
        }
        definitions.putIfAbsent("default", new LaunchpadDefinition(
                "default",
                "<gradient:#f59e0b:#f97316>Launchpad</gradient>",
                Material.SLIME_BLOCK,
                true,
                "DIRECTION",
                new Vector(0.0, 0.75, 0.0),
                1.0,
                null,
                "NORTH"
        ));

        YamlConfiguration placementsConfig = configs.get("world/launchpads.yml");
        ConfigurationSection placements = placementsConfig.getConfigurationSection("placements");
        if (placements != null) {
            for (String key : placements.getKeys(false)) {
                ConfigurationSection section = placements.getConfigurationSection(key);
                if (section == null) continue;
                String definitionId = section.getString("definition", "default");
                LaunchpadDefinition base = definitions.get(definitionId.toLowerCase(Locale.ROOT));
                Location location = readLocation(section);
                Location target = readLocation(section.getConfigurationSection("target"));
                if (base != null && location != null) {
                    Vector velocity = new Vector(
                            section.getDouble("velocity.x", base.velocity().getX()),
                            section.getDouble("velocity.y", base.velocity().getY()),
                            section.getDouble("velocity.z", base.velocity().getZ())
                    );
                    LaunchpadDefinition definition = new LaunchpadDefinition(
                            base.id(),
                            section.getString("display-name", base.displayName()),
                            base.material(),
                            section.getBoolean("enabled", base.enabled()),
                            section.getString("mode", base.mode()),
                            velocity,
                            section.getDouble("strength", base.strength()),
                            target != null ? target : base.target(),
                            base.facing()
                    );
                    placedPads.put(key(location), new PlacedLaunchpad(definition, location, target));
                }
            }
        }
    }

    public void openMenu(Player player) {
        menuService.open(player, buildMenu());
    }

    public Inventory buildMenu() {
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "launchpads"), 54, "3SMP Launchpads");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());

        int slot = 10;
        for (PlacedLaunchpad pad : placedPads.values()) {
            inv.setItem(slot, launchpadItem(pad));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35 || slot == 44) slot++;
            if (slot >= 45) break;
        }

        inv.setItem(49, button(Material.SLIME_BLOCK, "<gradient:#f59e0b:#f97316>Create New</gradient>", List.of("<gray>Give a launchpad item.</gray>", "<gray>Place it in the world, then configure it.</gray>")));
        inv.setItem(53, button(Material.BARRIER, "<red>Close</red>", List.of("<gray>Exit the launchpad browser.</gray>")));
        return inv;
    }

    public Inventory buildDetailMenu(String placementKey) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "launchpad:" + placementKey), 27, "Launchpad: " + (pad == null ? placementKey : pad.definition().displayName()));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());

        if (pad == null) {
            inv.setItem(13, button(Material.BARRIER, "<red>Missing</red>", List.of("<gray>This launchpad no longer exists.</gray>")));
            inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to launchpad list.</gray>")));
            return inv;
        }

        inv.setItem(11, button(Material.SLIME_BLOCK, "<gradient:#f59e0b:#f97316>Give Item</gradient>", List.of("<gray>Gives the configured launchpad item.</gray>")));
        inv.setItem(12, button(pad.definition().enabled() ? Material.LIME_DYE : Material.RED_DYE, pad.definition().enabled() ? "<green>Enabled</green>" : "<red>Disabled</red>", List.of("<gray>Toggle this launchpad in game.</gray>")));
        inv.setItem(13, button(Material.COMPASS, "<gradient:#60a5fa:#c084fc>Update Target</gradient>", List.of("<gray>Open the sign editor and enter coordinates.</gray>", "<gray>Use x, y, z and optional world.</gray>")));
        inv.setItem(14, button(Material.NAME_TAG, "<gradient:#60a5fa:#fbbf24>Rename</gradient>", List.of("<gray>Rename this placed launchpad.</gray>", "<gray>Use the sign editor for a clean in-game rename.</gray>", "<white>Current:</white> <aqua>" + pad.definition().displayName() + "</aqua>")));
        inv.setItem(15, button(Material.REDSTONE, "<gradient:#34d399:#22c55e>Launch Info</gradient>", List.of(
                "<gray>Enabled:</gray> <white>" + pad.definition().enabled() + "</white>",
                "<gray>Mode:</gray> <white>" + pad.definition().mode() + "</white>",
                "<gray>Velocity:</gray> <white>" + pad.definition().velocity().getX() + ", " + pad.definition().velocity().getY() + ", " + pad.definition().velocity().getZ() + "</white>",
                "<gray>Strength:</gray> <white>" + formatDelta(pad.definition().strength()) + "</white>",
                "<gray>Target:</gray> <white>" + (pad.target() == null ? "none" : prettyLocation(pad.target())) + "</white>"
        )));
        inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to launchpad list.</gray>")));
        return inv;
    }

    public void handleMenuClick(Player player, int slot) {
        if (slot == 49) {
            give(player, "default");
            Text.send(player, "<green>Given a launchpad item.</green>");
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        int index = slotIndex(slot);
        if (index >= 0 && index < new ArrayList<>(placedPads.keySet()).size()) {
            String key = new ArrayList<>(placedPads.keySet()).get(index);
            menuService.open(player, buildDetailMenu(key));
        }
    }

    public void handleDetailClick(Player player, String placementKey, int slot) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        if (pad == null) {
            menuService.open(player, buildMenu());
            return;
        }
        if (slot == 11) {
            give(player, pad.definition().id());
            Text.send(player, "<green>Given " + pad.definition().displayName() + ".</green>");
        } else if (slot == 12) {
            toggleEnabled(placementKey);
            openDetailMenu(player, placementKey);
        } else if (slot == 13) {
            openTargetEditor(player, placementKey);
        } else if (slot == 14) {
            openRenameEditor(player, placementKey);
        } else if (slot == 15) {
            Text.send(player, "<gray>Use the GUI buttons to toggle mode, target, rename, and enabled state.</gray>");
        } else if (slot == 22) {
            openMenu(player);
        }
    }

    public void give(Player player, String id) {
        player.getInventory().addItem(createItem(id));
    }

    public void openTargetEditor(Player player, String placementKey) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        if (pad == null) {
            Text.send(player, "<red>That launchpad no longer exists.</red>");
            openMenu(player);
            return;
        }
        Location signLocation = player.getLocation().getBlock().getLocation().add(0, 1, 0);
        var block = signLocation.getBlock();
        var previous = block.getBlockData().clone();
        block.setType(Material.OAK_SIGN);
        pendingTargetEdits.put(player.getUniqueId(), new PendingSignEdit(placementKey, signLocation, previous));
        try {
            if (block.getState() instanceof Sign sign) {
                player.openSign(sign, Side.FRONT);
                Text.send(player, "<gray>Enter coordinates on the sign: line 1 x, line 2 y, line 3 z, line 4 world (optional).</gray>");
            } else {
                Text.send(player, "<red>Could not open sign editor.</red>");
            }
        } catch (Throwable ex) {
            pendingTargetEdits.remove(player.getUniqueId());
            block.setBlockData(previous);
            Text.send(player, "<red>Could not open sign editor.</red>");
        }
    }

    public void openRenameEditor(Player player, String placementKey) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        if (pad == null) {
            Text.send(player, "<red>That launchpad no longer exists.</red>");
            openMenu(player);
            return;
        }
        Location signLocation = player.getLocation().getBlock().getLocation().add(0, 1, 0);
        var block = signLocation.getBlock();
        var previous = block.getBlockData().clone();
        block.setType(Material.OAK_SIGN);
        pendingRenameEdits.put(player.getUniqueId(), new PendingRenameEdit(placementKey, signLocation, previous));
        try {
            if (block.getState() instanceof Sign sign) {
                sign.getSide(Side.FRONT).setLine(0, "Launchpad");
                sign.getSide(Side.FRONT).setLine(1, "Rename");
                sign.getSide(Side.FRONT).setLine(2, "Line 1 = name");
                sign.getSide(Side.FRONT).setLine(3, "");
                sign.update(true, false);
                player.openSign(sign, Side.FRONT);
                Text.send(player, "<gray>Enter the new launchpad name on line 1.</gray>");
            } else {
                Text.send(player, "<red>Could not open sign editor.</red>");
            }
        } catch (Throwable ex) {
            pendingRenameEdits.remove(player.getUniqueId());
            block.setBlockData(previous);
            Text.send(player, "<red>Could not open sign editor.</red>");
        }
    }

    public ItemStack createItem(String id) {
        LaunchpadDefinition definition = definitions.get(id.toLowerCase(Locale.ROOT));
        if (definition == null) definition = definitions.get("default");
        if (definition == null) return new ItemStack(Material.SLIME_BLOCK);
        ItemStack item = new ItemStack(definition.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(definition.displayName()));
        meta.lore(List.of(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Place to create a launchpad.</gray>"),
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Right-click or step on the placed pad to launch.</gray>")
        ));
        meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }

    public List<String> ids() {
        return List.copyOf(definitions.keySet());
    }

    public void setTarget(String id, Location target) {
        YamlConfiguration yaml = configs.get("core/config.yml");
        yaml.set("launchpads." + id + ".target.world", target.getWorld() == null ? "world" : target.getWorld().getName());
        yaml.set("launchpads." + id + ".target.x", target.getX());
        yaml.set("launchpads." + id + ".target.y", target.getY());
        yaml.set("launchpads." + id + ".target.z", target.getZ());
        yaml.set("launchpads." + id + ".target.yaw", target.getYaw());
        yaml.set("launchpads." + id + ".target.pitch", target.getPitch());
        saveConfigFile(yaml, "core/config.yml");
        reload();
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        String id = itemId(event.getItemInHand());
        if (id == null) return;
        if (!canEdit(event.getPlayer())) {
            Text.send(event.getPlayer(), "<red>No permission.</red>");
            event.setCancelled(true);
            return;
        }
        LaunchpadDefinition definition = definitions.getOrDefault(id, definitions.get("default"));
        if (definition == null || !definition.enabled()) return;
        storePlacement(definition, event.getBlockPlaced().getLocation(), null);
        event.getBlockPlaced().setType(Material.SLIME_BLOCK);
        Text.send(event.getPlayer(), "<green>Launchpad placed.</green>");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String key = key(event.getBlock().getLocation());
        if (!placedPads.containsKey(key)) return;
        if (!canEdit(event.getPlayer())) {
            event.setCancelled(true);
            Text.send(event.getPlayer(), "<red>No permission.</red>");
            return;
        }
        removePlacement(event.getBlock().getLocation());
        Text.send(event.getPlayer(), "<yellow>Launchpad removed.</yellow>");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        PlacedLaunchpad pad = placedPads.get(key(block.getLocation()));
        if (pad == null) {
            Location below = block.getLocation().clone().subtract(0, 1, 0);
            pad = placedPads.get(key(below));
        }
        if (pad == null) return;
        if (event.getPlayer().isSneaking() && canEdit(event.getPlayer())) {
            event.setCancelled(true);
            menuService.open(event.getPlayer(), buildDetailMenu(key(pad.location())));
            return;
        }
        triggerLaunch(event.getPlayer(), block.getLocation(), event);
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        PendingSignEdit edit = pendingTargetEdits.remove(event.getPlayer().getUniqueId());
        if (edit != null) {
            if (!edit.location().equals(event.getBlock().getLocation())) return;
            List<String> lines = new ArrayList<>();
            for (String line : event.getLines()) lines.add(line == null ? "" : line.trim());
            if (lines.size() < 3 || lines.get(0).isBlank() || lines.get(1).isBlank() || lines.get(2).isBlank()) {
                event.getBlock().setBlockData(edit.previous());
                Text.send(event.getPlayer(), "<red>You must enter x, y, and z on the sign.</red>");
                return;
            }
            try {
                double x = Double.parseDouble(lines.get(0));
                double y = Double.parseDouble(lines.get(1));
                double z = Double.parseDouble(lines.get(2));
                String worldName = lines.size() >= 4 && !lines.get(3).isBlank() ? lines.get(3) : event.getPlayer().getWorld().getName();
                var world = Bukkit.getWorld(worldName);
                if (world == null) {
                    event.getBlock().setBlockData(edit.previous());
                    Text.send(event.getPlayer(), "<red>World not found.</red>");
                    return;
                }
                Location target = new Location(world, x, y, z, event.getPlayer().getYaw(), event.getPlayer().getPitch());
                PlacedLaunchpad pad = placedPads.get(edit.placementKey());
                if (pad == null) {
                    event.getBlock().setBlockData(edit.previous());
                    Text.send(event.getPlayer(), "<red>Launchpad disappeared.</red>");
                    return;
                }
                setTargetPlacement(edit.placementKey(), target);
                event.getBlock().setBlockData(edit.previous());
                Text.send(event.getPlayer(), "<green>Launchpad target saved.</green>");
            } catch (Exception ex) {
                event.getBlock().setBlockData(edit.previous());
                Text.send(event.getPlayer(), "<red>Invalid sign input.</red>");
            }
            return;
        }

        PendingRenameEdit rename = pendingRenameEdits.remove(event.getPlayer().getUniqueId());
        if (rename == null) return;
        if (!rename.location().equals(event.getBlock().getLocation())) return;
        String name = event.getLine(0) == null ? "" : event.getLine(0).trim();
        if (name.isBlank()) {
            event.getBlock().setBlockData(rename.previous());
            Text.send(event.getPlayer(), "<red>You must enter a launchpad name.</red>");
            return;
        }
        try {
            setDisplayName(rename.placementKey(), name);
            event.getBlock().setBlockData(rename.previous());
            Text.send(event.getPlayer(), "<green>Launchpad renamed.</green>");
        } catch (Exception ex) {
            event.getBlock().setBlockData(rename.previous());
            Text.send(event.getPlayer(), "<red>Invalid launchpad name.</red>");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        triggerLaunch(event.getPlayer(), event.getTo(), null);
    }

    private void triggerLaunch(Player player, Location location, PlayerInteractEvent interactEvent) {
        PlacedLaunchpad launchpad = placedPads.get(key(location));
        if (launchpad == null) {
            Location below = location.clone().subtract(0, 1, 0);
            launchpad = placedPads.get(key(below));
            if (launchpad == null) return;
        }
        if (interactEvent != null) interactEvent.setCancelled(true);
        if (player.hasMetadata("launchpad_cooldown")) return;
        if (!launchpad.definition().enabled()) return;
        player.setMetadata("launchpad_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        String particleId = perkService == null ? "" : perkService.data(player.getUniqueId()).activeParticle();
        playLaunchParticles(player, particleId);
        Vector velocity = solveLaunchVelocity(launchpad.location().clone().add(0.5, 1.0, 0.5), player.getLocation(), launchpad.target(), launchpad.definition().velocity(), launchpad.definition().strength());
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);
        player.setSneaking(false);
        player.setGliding(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setVelocity(velocity);
                player.setFallDistance(0.0f);
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.removeMetadata("launchpad_cooldown", plugin);
        }, 12L);
    }

    private void storePlacement(LaunchpadDefinition definition, Location location, Location target) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + key(location);
        yaml.set(path + ".definition", definition.id());
        yaml.set(path + ".display-name", definition.displayName());
        writeLocation(yaml, path, location);
        if (target != null) writeLocation(yaml, path + ".target", target);
        yaml.set(path + ".facing", definition.facing());
        yaml.set(path + ".mode", definition.mode());
        yaml.set(path + ".strength", definition.strength());
        yaml.set(path + ".velocity.x", definition.velocity().getX());
        yaml.set(path + ".velocity.y", definition.velocity().getY());
        yaml.set(path + ".velocity.z", definition.velocity().getZ());
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    private void removePlacement(Location location) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        yaml.set("placements." + key(location), null);
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    private boolean canEdit(Player player) {
        return player.isOp() || player.hasPermission("3smpcore.launchpad.admin");
    }

    private void writeLocation(YamlConfiguration yaml, String path, Location location) {
        yaml.set(path + ".world", location.getWorld() == null ? "world" : location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", location.getYaw());
        yaml.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) return null;
        var world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) return null;
        return new Location(world, section.getDouble("x", world.getSpawnLocation().getX()), section.getDouble("y", world.getSpawnLocation().getY()), section.getDouble("z", world.getSpawnLocation().getZ()), (float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
    }

    private void saveConfigFile(YamlConfiguration yaml, String fileName) {
        try {
            yaml.save(new File(plugin.getDataFolder(), fileName));
        } catch (Exception ignored) {
        }
    }

    private String key(Location location) {
        if (location.getWorld() == null) return "unknown:" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        return location.getWorld().getName().toLowerCase(Locale.ROOT) + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private int slotIndex(int slot) {
        if (slot < 10 || slot > 44) return -1;
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) return i;
        return -1;
    }

    private String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }

    private NamespacedKey key() {
        return new NamespacedKey(plugin, ITEM_KEY);
    }

    private Material parseMaterial(String input) {
        try {
            return Material.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return Material.SLIME_BLOCK;
        }
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack launchpadItem(PlacedLaunchpad pad) {
        ItemStack item = new ItemStack(Material.SLIME_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(pad.definition().displayName()));
        meta.lore(List.of(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<gray>Click for details.</gray>"),
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<dark_gray>" + prettyLocation(pad.location()) + "</dark_gray>")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name));
        meta.lore(lore.stream().map(s -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(s)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String prettyLocation(Location location) {
        return location == null || location.getWorld() == null ? "unknown" : location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String formatVector(Vector vector) {
        if (vector == null) return "0.0, 0.75, 1.8";
        return String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vector.getX(), vector.getY(), vector.getZ());
    }

    private void toggleEnabled(String placementKey) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        if (pad == null) return;
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        yaml.set(path + ".enabled", !pad.definition().enabled());
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    public Inventory buildDirectionMenu(String placementKey) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        Inventory inv = Bukkit.createInventory(new CoreMenuHolder(CoreMenuType.DUEL_DEV, "launchpad-direction:" + placementKey), 27, "Launchpad Direction");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane());
        if (pad == null) {
            inv.setItem(13, button(Material.BARRIER, "<red>Missing</red>", List.of("<gray>This launchpad no longer exists.</gray>")));
        } else {
            inv.setItem(4, button(Material.SLIME_BLOCK, "<gradient:#1A2A4A:#D6E8F7>Current Vector</gradient>", List.of(
                    "<gray>Mode:</gray> <white>" + pad.definition().mode() + "</white>",
                    "<gray>Vector:</gray> <white>" + formatVector(pad.definition().velocity()) + "</white>",
                    "<gray>Strength:</gray> <white>" + formatDelta(pad.definition().strength()) + "</white>",
                    "<gray>Target:</gray> <white>" + (pad.target() == null ? "none" : prettyLocation(pad.target())) + "</white>"
            )));
            inv.setItem(10, button(Material.RED_WOOL, "<gradient:#60a5fa:#c084fc>X-</gradient>", List.of("<gray>Decrease horizontal push to the west.</gray>", "<white>Current X: " + formatDelta(pad.definition().velocity().getX()) + "</white>")));
            inv.setItem(11, button(Material.LIME_WOOL, "<gradient:#60a5fa:#c084fc>X+</gradient>", List.of("<gray>Increase horizontal push to the east.</gray>", "<white>Current X: " + formatDelta(pad.definition().velocity().getX()) + "</white>")));
            inv.setItem(12, button(Material.RED_WOOL, "<gradient:#60a5fa:#c084fc>Y-</gradient>", List.of("<gray>Lower the lift strength.</gray>", "<white>Current Y: " + formatDelta(pad.definition().velocity().getY()) + "</white>")));
            inv.setItem(13, button(Material.LIME_WOOL, "<gradient:#60a5fa:#c084fc>Y+</gradient>", List.of("<gray>Raise the lift strength.</gray>", "<white>Current Y: " + formatDelta(pad.definition().velocity().getY()) + "</white>")));
            inv.setItem(14, button(Material.RED_WOOL, "<gradient:#60a5fa:#c084fc>Z-</gradient>", List.of("<gray>Decrease push to the north.</gray>", "<white>Current Z: " + formatDelta(pad.definition().velocity().getZ()) + "</white>")));
            inv.setItem(15, button(Material.LIME_WOOL, "<gradient:#60a5fa:#c084fc>Z+</gradient>", List.of("<gray>Increase push to the south.</gray>", "<white>Current Z: " + formatDelta(pad.definition().velocity().getZ()) + "</white>")));
            inv.setItem(16, button(Material.SLIME_BLOCK, "<gradient:#f59e0b:#f97316>Boost All</gradient>", List.of("<gray>Increase the entire vector slightly.</gray>", "<white>Current: " + formatVector(pad.definition().velocity()) + "</white>")));
            inv.setItem(17, button(Material.FIREWORK_ROCKET, "<gradient:#f59e0b:#fbbf24>Strength -</gradient>", List.of("<gray>Reduce launch strength.</gray>", "<white>Current Strength: " + formatDelta(pad.definition().strength()) + "</white>")));
            inv.setItem(18, button(Material.ENDER_PEARL, "<gradient:#34d399:#22c55e>Target Mode</gradient>", List.of("<gray>Switch to teleport target mode.</gray>", "<gray>Uses the sign-entered coordinate.</gray>")));
            inv.setItem(19, button(Material.FIREWORK_ROCKET, "<gradient:#f59e0b:#fbbf24>Strength +</gradient>", List.of("<gray>Increase launch strength.</gray>", "<white>Current Strength: " + formatDelta(pad.definition().strength()) + "</white>")));
            inv.setItem(20, button(Material.ENDER_PEARL, "<gradient:#f59e0b:#f97316>Vertical Boost</gradient>", List.of("<gray>Launch straight up.</gray>", "<white>Vector: 0.0, 2.0, 0.0</white>")));
            inv.setItem(22, button(Material.ARROW, "<gray>Back</gray>", List.of("<gray>Return to launchpad details.</gray>")));
        }
        return inv;
    }

    public void handleDirectionClick(Player player, String placementKey, int slot) {
        PlacedLaunchpad pad = placedPads.get(placementKey);
        if (pad == null) { openMenu(player); return; }
        if (slot == 22) { openDetailMenu(player, placementKey); return; }
        if (slot == 18) {
            setMode(placementKey, "TARGET");
            reload();
            openDirectionEditor(player, placementKey);
            return;
        }
        if (slot == 20) {
            setMode(placementKey, "DIRECTION");
            setVector(placementKey, 0.0, 0.75, 1.8);
            reload();
            openDirectionEditor(player, placementKey);
            return;
        }
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        Vector velocity = pad.definition().velocity().clone();
        double step = 0.5;
        if (slot == 10) velocity.setX(velocity.getX() - step);
        else if (slot == 11) velocity.setX(velocity.getX() + step);
        else if (slot == 12) velocity.setY(velocity.getY() - step);
        else if (slot == 13) velocity.setY(velocity.getY() + step);
        else if (slot == 14) velocity.setZ(velocity.getZ() - step);
        else if (slot == 15) velocity.setZ(velocity.getZ() + step);
        else if (slot == 16) velocity.add(new Vector(0.50, 0.25, 0.50));
        if (slot >= 10 && slot <= 16) {
            setMode(placementKey, "DIRECTION");
            yaml.set(path + ".velocity.x", velocity.getX());
            yaml.set(path + ".velocity.y", velocity.getY());
            yaml.set(path + ".velocity.z", velocity.getZ());
            saveConfigFile(yaml, "world/launchpads.yml");
        } else if (slot == 17) {
            setStrength(placementKey, Math.max(0.10, pad.definition().strength() - 0.25));
        } else if (slot == 19) {
            setStrength(placementKey, pad.definition().strength() + 0.25);
        }
        reload();
        openDirectionEditor(player, placementKey);
    }

    private void openDirectionEditor(Player player, String placementKey) { menuService.open(player, buildDirectionMenu(placementKey)); }
    private void playLaunchParticles(Player player, String particleId) {
        if (particleId == null || particleId.isBlank()) return;
        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(configs.get("cosmetics/particles.yml").getString("particles." + particleId + ".type", "ENCHANTMENT_TABLE"));
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 24, 0.45, 0.45, 0.45, 0.02);
        } catch (Exception ignored) {
            player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, player.getLocation().add(0, 1, 0), 24, 0.45, 0.45, 0.45, 0.02);
        }
    }

    private void setMode(String placementKey, String mode) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        yaml.set(path + ".mode", mode);
        saveConfigFile(yaml, "world/launchpads.yml");
    }

    private void setDisplayName(String placementKey, String displayName) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        yaml.set(path + ".display-name", displayName);
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    private void setStrength(String placementKey, double strength) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        yaml.set(path + ".strength", strength);
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    private void setVector(String placementKey, double x, double y, double z) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        yaml.set(path + ".mode", "DIRECTION");
        yaml.set(path + ".velocity.x", x);
        yaml.set(path + ".velocity.y", y);
        yaml.set(path + ".velocity.z", z);
        saveConfigFile(yaml, "world/launchpads.yml");
    }

    private Vector solveLaunchVelocity(Location padLocation, Location playerLocation, Location target, Vector fallback, double strength) {
        Vector fallbackVelocity = fallback == null ? new Vector(0.0, 0.95, 1.8) : fallback.clone();
        double multiplier = Math.max(0.1, strength);
        if (target == null || target.getWorld() == null || padLocation == null || padLocation.getWorld() == null || !padLocation.getWorld().equals(target.getWorld())) {
            return fallbackVelocity.multiply(multiplier);
        }

        Location start = playerLocation == null ? padLocation : playerLocation.clone();
        start.setX(padLocation.getX());
        start.setY(Math.max(start.getY(), padLocation.getY()));
        start.setZ(padLocation.getZ());

        double dx = target.getX() + 0.5 - start.getX();
        double dz = target.getZ() + 0.5 - start.getZ();
        double dy = target.getY() - start.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.01 && Math.abs(dy) < 0.01) return new Vector(0.0, 0.6, 0.0);

        double minTicks = configs.get("world/launchpads.yml").getDouble("physics.min-flight-ticks", 12.0);
        double maxTicks = configs.get("world/launchpads.yml").getDouble("physics.max-flight-ticks", 52.0);
        double horizontalBlocksPerTick = configs.get("world/launchpads.yml").getDouble("physics.horizontal-blocks-per-tick", 1.15) * multiplier;
        double gravity = configs.get("world/launchpads.yml").getDouble("physics.gravity-per-tick", 0.08);
        double maxHorizontal = configs.get("world/launchpads.yml").getDouble("physics.max-horizontal-velocity", 4.2) * multiplier;
        double maxVertical = configs.get("world/launchpads.yml").getDouble("physics.max-vertical-velocity", 5.0) * multiplier;

        double ticks = clamp(horizontal / Math.max(0.05, horizontalBlocksPerTick), minTicks, maxTicks);
        double vx = dx / ticks;
        double vz = dz / ticks;
        double vy = (dy + 0.5 * gravity * ticks * ticks) / ticks;

        Vector horizontalVector = new Vector(vx, 0.0, vz);
        if (horizontalVector.length() > maxHorizontal) horizontalVector.normalize().multiply(maxHorizontal);
        vy = clamp(vy, 0.35, maxVertical);

        return new Vector(horizontalVector.getX(), vy, horizontalVector.getZ());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatDelta(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private double velocityMagnitude(Vector vector) {
        if (vector == null) return 1.8;
        return Math.max(0.5, Math.sqrt(vector.getX() * vector.getX() + vector.getY() * vector.getY() + vector.getZ() * vector.getZ()));
    }

    private void setTargetPlacement(String placementKey, Location target) {
        YamlConfiguration yaml = configs.get("world/launchpads.yml");
        String path = "placements." + placementKey;
        writeLocation(yaml, path + ".target", target);
        saveConfigFile(yaml, "world/launchpads.yml");
        reload();
    }

    private void openDetailMenu(Player player, String placementKey) { menuService.open(player, buildDetailMenu(placementKey)); }
    private String placementKeyFromPad(PlacedLaunchpad pad) { return key(pad.location()); }
    private record LaunchpadDefinition(String id, String displayName, Material material, boolean enabled, String mode, Vector velocity, double strength, Location target, String facing) { }
    private record PlacedLaunchpad(LaunchpadDefinition definition, Location location, Location target) { }
    private record PendingSignEdit(String placementKey, Location location, org.bukkit.block.data.BlockData previous) { }
    private record PendingRenameEdit(String placementKey, Location location, org.bukkit.block.data.BlockData previous) { }
}



