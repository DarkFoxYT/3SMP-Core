package net.dark.threecore.command;

import net.dark.threecore.chat.ChatFormatService;
import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.gems.GemService;
import net.dark.threecore.perks.PerkService;
import net.dark.threecore.help.HelpService;
import net.dark.threecore.sapphires.SapphireService;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandRegistrar {
    private final JavaPlugin plugin;
    private final ConfigFiles configs;
    private final PerkService perkService;
    private final SapphireService sapphireService;
    private final GemService gemService;
    private final ChatFormatService chatFormatService;
    private final HelpService helpService;

    public CommandRegistrar(JavaPlugin plugin, ConfigFiles configs, PerkService perkService, SapphireService sapphireService, GemService gemService, ChatFormatService chatFormatService) {
        this.plugin = plugin;
        this.configs = configs;
        this.perkService = perkService;
        this.sapphireService = sapphireService;
        this.gemService = gemService;
        this.chatFormatService = chatFormatService;
        this.helpService = new HelpService(plugin, configs);
    }

    public void registerAll() {
        register("perks", perkService::handleCommand);
        register("sapphire", sapphireService::handleCommand);
        register("sap", sapphireService::handleCommand);
        register("sapphires", sapphireService::handleCommand);
        register("gem", gemService::handleCommand);
        register("gems", gemService::handleCommand);
        registerHelp();
    }

    private void register(String name, CommandHandler handler) {
        var command = plugin.getCommand(name);
        if (command == null) return;
        command.setExecutor((sender, command1, label, args) -> {
            handler.handle(sender, args);
            return true;
        });
    }

    private void registerHelp() {
        var command = plugin.getCommand("help");
        if (command == null) return;
        command.setExecutor((sender, command1, label, args) -> {
            helpService.handle(sender, args);
            return true;
        });
        command.setTabCompleter((sender, command1, label, args) -> helpService.complete(args));
    }

    @FunctionalInterface
    private interface CommandHandler {
        void handle(CommandSender sender, String[] args);
    }
}
