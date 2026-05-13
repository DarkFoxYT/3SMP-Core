package net.dark.threecore.visual;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GradientRenderer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final VisualConfig config;

    public GradientRenderer(VisualConfig config) {
        this.config = config;
    }

    public Component render(Player player, String input, RankStyle rank) {
        return render(player, input, rank, "");
    }

    public Component render(Player player, String input, RankStyle rank, String shadow) {
        Component component = LEGACY.deserialize(renderLegacy(player, input, rank));
        ShadowColor parsed = parseShadow(shadow);
        return parsed == null ? component : component.shadowColor(parsed);
    }

    public String renderLegacy(Player player, String input, RankStyle rank) {
        String parsed = input == null ? "" : input;
        parsed = internalPlaceholders(player, parsed);
        parsed = replaceRankTokens(parsed, player, rank);
        parsed = imageTags(parsed);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }
        parsed = imageTags(parsed);
        if (config.stripMissingImages()) parsed = stripUnresolvedImages(parsed);
        parsed = renderGradientTags(parsed, rank);
        return legacyColors(parsed);
    }

    public String renderLegacyForWidth(Player player, String input, RankStyle rank) {
        String parsed = input == null ? "" : input;
        parsed = internalPlaceholders(player, parsed);
        parsed = replaceRankTokens(parsed, player, rank);
        parsed = imageTags(parsed);
        parsed = protectImagePlaceholders(parsed);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            parsed = PlaceholderAPI.setPlaceholders(player, parsed);
        }
        parsed = restoreImagePlaceholders(parsed);
        parsed = imageTags(parsed);
        if (config.stripMissingImages()) parsed = stripUnresolvedImages(parsed);
        parsed = renderGradientTags(parsed, rank);
        return legacyColors(parsed);
    }

    public String plainDebug(Player player, String input, RankStyle rank) {
        return LEGACY.serialize(render(player, input, rank)).replaceAll("§x(§[0-9a-fA-F]){6}", "").replaceAll("§.", "");
    }

    private String replaceRankTokens(String input, Player player, RankStyle rank) {
        String name = player == null ? "" : player.getName();
        return input
            .replace("<rank_image>", rank.image())
            .replace("<rank_prefix>", rank.prefix())
            .replace("<tab_prefix>", rank.tabPrefix())
            .replace("<player>", name)
            .replace("<tab_tag>", "");
    }

    private String internalPlaceholders(Player player, String input) {
        if (player == null) return input;
        String world = player.getWorld() == null ? "" : player.getWorld().getName();
        return input
            .replace("%world%", world)
            .replace("%player_world%", world)
            .replace("%3smpcore_world%", world)
            .replace("%3smpcore_world_clean%", config.worldDisplayName(world))
            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("%3smpcore_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("%3smpcore_max_players%", String.valueOf(Bukkit.getMaxPlayers()))
            .replace("%player_name%", player.getName())
            .replace("%ping%", player.getPing() + "ms");
    }

    private String imageTags(String input) {
        String out = input;
        for (String id : config.imageIds()) {
            String value = config.image(id);
            out = out.replace("<img:" + id + ">", value);
        }
        return out;
    }

    private String protectImagePlaceholders(String input) {
        String out = input;
        for (String id : config.imageIds()) {
            String token = imageWidthToken(id);
            String placeholder = config.image(id);
            if (placeholder != null && !placeholder.isBlank()) out = out.replace(placeholder, token);
            out = out.replace("%img_" + id + "%", token);
        }
        return out;
    }

    private String restoreImagePlaceholders(String input) {
        String out = input;
        for (String id : config.imageIds()) {
            out = out.replace(imageWidthToken(id), "%img_" + id + "%");
        }
        return out;
    }

    private String imageWidthToken(String id) {
        return "{3smp_img_width:" + id + "}";
    }

    private String stripUnresolvedImages(String input) {
        return input.replaceAll("%img_[A-Za-z0-9_]+%", "");
    }

    private String renderGradientTags(String input, RankStyle rank) {
        String out = input;
        int guard = 0;
        while (guard++ < 32) {
            int start = out.lastIndexOf("{grad:");
            if (start < 0) break;
            int idEnd = out.indexOf('}', start);
            int close = out.indexOf("{/grad}", idEnd + 1);
            if (idEnd < 0 || close < 0) break;
            String id = out.substring(start + 6, idEnd).toLowerCase(Locale.ROOT);
            if (id.equals("rank")) id = rank.gradient();
            String body = out.substring(idEnd + 1, close);
            String rendered = gradient(body, config.gradient(id));
            out = out.substring(0, start) + rendered + out.substring(close + 7);
        }
        return out.replace("{/grad}", "");
    }

    private String gradient(String text, String spec) {
        List<Color> colors = colors(spec);
        if (colors.isEmpty()) colors = colors(config.gradient("default"));
        int visible = visibleLength(text);
        if (visible <= 0) return legacyColors(text);
        StringBuilder out = new StringBuilder();
        StringBuilder formats = new StringBuilder();
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '#' && i + 7 < text.length()) {
                    i += 7;
                    continue;
                }
                char code = Character.toLowerCase(text.charAt(++i));
                if ("lmnok".indexOf(code) >= 0) formats.append('§').append(code);
                else if (code == 'r') formats.setLength(0);
                continue;
            }
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if ("lmnok".indexOf(code) >= 0) formats.append('§').append(code);
                else if (code == 'r') formats.setLength(0);
                continue;
            }
            Color color = interpolate(colors, visible == 1 ? 0.0D : (double) index / (double) (visible - 1));
            out.append(sectionHex(color)).append(formats).append(c);
            index++;
        }
        return out.toString();
    }

    private int visibleLength(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '#' && i + 7 < text.length()) i += 7;
                else i++;
                continue;
            }
            count++;
        }
        return count;
    }

    private List<Color> colors(String spec) {
        List<Color> out = new ArrayList<>();
        for (String part : (spec == null ? "" : spec).split(":")) {
            String hex = part.trim().replace("#", "");
            if (hex.length() != 6) continue;
            try {
                out.add(new Color(Integer.parseInt(hex, 16)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private Color interpolate(List<Color> colors, double t) {
        if (colors.size() == 1) return colors.get(0);
        double scaled = t * (colors.size() - 1);
        int left = Math.min(colors.size() - 2, (int) Math.floor(scaled));
        double local = scaled - left;
        Color a = colors.get(left);
        Color b = colors.get(left + 1);
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * local);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * local);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * local);
        return new Color(r, g, bl);
    }

    public String legacyColors(String input) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&' && i + 1 < input.length()) {
                if (input.charAt(i + 1) == '#' && i + 7 < input.length()) {
                    String hex = input.substring(i + 2, i + 8);
                    try {
                        out.append(sectionHex(new Color(Integer.parseInt(hex, 16))));
                        i += 7;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
                out.append('§').append(input.charAt(++i));
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String sectionHex(Color color) {
        String hex = String.format("%06x", color.getRGB() & 0xFFFFFF);
        StringBuilder out = new StringBuilder("§x");
        for (char c : hex.toCharArray()) out.append('§').append(c);
        return out.toString();
    }

    private ShadowColor parseShadow(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.replace("#", "").split(":");
        if (parts.length == 0 || parts[0].length() != 6) return null;
        try {
            int rgb = Integer.parseInt(parts[0], 16);
            int alpha = 180;
            if (parts.length > 1) {
                double parsed = Double.parseDouble(parts[1]);
                alpha = parsed <= 1.0D ? (int) Math.round(parsed * 255.0D) : (int) Math.round(parsed);
            }
            return ShadowColor.shadowColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.max(0, Math.min(255, alpha)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
