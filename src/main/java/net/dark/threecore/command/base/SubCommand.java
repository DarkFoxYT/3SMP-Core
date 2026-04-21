package net.dark.threecore.command.base;

import org.bukkit.command.CommandSender;

public interface SubCommand {
    String name();
    String permission();
    String description();
    default String usage() { return name(); }
    void execute(CommandContext context);
    default java.util.List<String> tabComplete(CommandContext context) { return java.util.List.of(); }
    default boolean canUse(CommandSender sender) { return permission() == null || permission().isBlank() || sender.hasPermission(permission()); }
}
