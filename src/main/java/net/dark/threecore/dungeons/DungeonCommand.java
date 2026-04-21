package net.dark.threecore.dungeons;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class DungeonCommand implements CommandExecutor, TabCompleter {
    private static final List<String> TYPES = List.of("entrence", "room", "exit", "boss");

    private final DungeonManager manager;

    public DungeonCommand(DungeonManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            manager.openMenu(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("forceexit") && player.hasPermission("dunguons3smp.admin")) {
            manager.forceExitDungeon(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("devmode") && player.hasPermission("dunguons3smp.admin")) {
            manager.toggleDevMode(player);
            return true;
        }

        if (!args[0].equalsIgnoreCase("room")) {
            player.sendMessage(ChatColor.YELLOW + "/dungeon forceexit | devmode | room pos1 | pos2 | inspect | preview | save <level> <type> <name>");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "/dungeon forceexit | devmode | room pos1 | pos2 | inspect | preview | save <level> <type> <name>");
            return true;
        }

        if (args[1].equalsIgnoreCase("pos1")) {
            manager.setRoomPos1(player, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Room pos1 set to the block you're looking at.");
            return true;
        }

        if (args[1].equalsIgnoreCase("pos2")) {
            manager.setRoomPos2(player, player.getLocation());
            player.sendMessage(ChatColor.GREEN + "Room pos2 set to the block you're looking at.");
            return true;
        }

        if (args[1].equalsIgnoreCase("inspect")) {
            manager.inspectSelection(player);
            return true;
        }

        if (args[1].equalsIgnoreCase("preview")) {
            manager.previewSelection(player);
            return true;
        }

        if (args[1].equalsIgnoreCase("save")) {
            if (args.length < 5) {
                player.sendMessage(ChatColor.YELLOW + "/dungeon room save <level> <type> <name>");
                player.sendMessage(ChatColor.GRAY + "Types: entrence, room, exit, boss");
                return true;
            }
            int level;
            try {
                level = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                player.sendMessage(ChatColor.RED + "Level must be a number.");
                return true;
            }
            manager.saveRoomTemplate(player, level, args[3], args[4]);
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "/dungeon forceexit | devmode | room pos1 | pos2 | preview | save <level> <type> <name>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("menu");
            out.add("forceexit");
            out.add("devmode");
            out.add("room");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("room")) {
            out.add("pos1");
            out.add("pos2");
            out.add("inspect");
            out.add("preview");
            out.add("save");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("save")) {
            out.add("1");
            out.add("2");
            out.add("3");
            out.add("4");
            out.add("5");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("room") && args[1].equalsIgnoreCase("save")) {
            out.addAll(TYPES);
        }
        return out;
    }
}