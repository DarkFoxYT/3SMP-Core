package net.dark.threecore.command.base;

import org.bukkit.command.CommandSender;

import java.util.List;

public record CommandContext(CommandSender sender, String label, String[] args, List<String> path) {
    public String arg(int index) { return index < args.length ? args[index] : ""; }
}
