package net.dark.threecore.command.base;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class CommandTree {
    private final List<SubCommand> children = new ArrayList<>();
    private final String name;
    private final String permission;
    private final String description;

    public CommandTree(String name, String permission, String description) {
        this.name = name;
        this.permission = permission;
        this.description = description;
    }

    public void add(SubCommand command) { children.add(command); }
    public List<SubCommand> children() { return children; }
    public String name() { return name; }
    public String permission() { return permission; }
    public String description() { return description; }
    public boolean canUse(CommandSender sender) { return permission == null || permission.isBlank() || sender.hasPermission(permission); }
}
