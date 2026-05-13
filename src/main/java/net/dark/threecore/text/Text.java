package net.dark.threecore.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Text {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String PREFIX = "<gradient:#f4cd2a:#eda323:#d28d0d>3SMP</gradient><white>></white> ";

    private Text() {}

    public static Component mm(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input);
    }

    public static Component prefixed(String input) {
        return mm(PREFIX + (input == null ? "" : input));
    }

    public static void send(CommandSender sender, String input) {
        sender.sendMessage(prefixed(input));
    }

    public static void raw(CommandSender sender, String input) {
        sender.sendMessage(mm(input));
    }

    public static void actionBar(Player player, String input) {
        player.sendActionBar(prefixed(input));
    }
}
