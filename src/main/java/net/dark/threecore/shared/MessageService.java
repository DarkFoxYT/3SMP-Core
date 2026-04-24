package net.dark.threecore.shared;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageService {
    private final ConfigFiles configs;

    public MessageService(ConfigFiles configs) {
        this.configs = configs;
    }

    public Component format(String key, String fallback) {
        return Text.mm(configs.get("messages.yml").getString(key, fallback));
    }

    public void send(CommandSender sender, String key, String fallback) {
        sender.sendMessage(format(key, fallback));
    }

    public void actionBar(Player player, String key, String fallback) {
        player.sendActionBar(format(key, fallback));
    }

    public String raw(String key, String fallback) {
        return configs.get("messages.yml").getString(key, fallback);
    }
}
