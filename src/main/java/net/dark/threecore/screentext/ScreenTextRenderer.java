package net.dark.threecore.screentext;

import me.clip.placeholderapi.PlaceholderAPI;
import net.dark.threecore.ThreeSMPCorePlugin;
import net.dark.threecore.money.MoneyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

public final class ScreenTextRenderer {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final char COLOR = '\u00A7';
    private final JavaPlugin plugin;
    private final ScreenTextRegistry registry;
    private final MoneyService moneyService;

    public ScreenTextRenderer(JavaPlugin plugin, ScreenTextRegistry registry, MoneyService moneyService) {
        this.plugin = plugin;
        this.registry = registry;
        this.moneyService = moneyService;
    }

    public RenderedFrame render(Player player, ScreenText text, long ageMillis, Map<String, BiFunction<Player, ScreenText, String>> customPlaceholders) {
        String legacy = renderLegacy(player, text, ageMillis, customPlaceholders);
        Component component = LEGACY.deserialize(legacy);
        ShadowColor shadow = shadow(text.style().shadow());
        if (shadow != null) component = component.shadowColor(shadow);
        return new RenderedFrame(component, legacy, List.of(legacy.split("\n", -1)));
    }

    public String renderLegacy(Player player, ScreenText text, long ageMillis, Map<String, BiFunction<Player, ScreenText, String>> customPlaceholders) {
        String raw = resolvePlaceholders(player, text, text.content(), customPlaceholders);
        raw = applyAnimationRaw(raw, text.animation(), ageMillis);
        raw = wrap(raw, text.style().maxWidth());
        raw = applyLineSpacing(raw, text.style().lineSpacing());
        raw = applyAlignment(raw, text.style().alignment(), text.style().maxWidth());
        raw = applyLetterSpacing(raw, text.style().letterSpacing());
        raw = applyStyle(raw, text.style());
        raw = renderRainbow(raw, text.style().rainbow() && registry.rainbowEnabled());
        raw = renderGradientTags(raw);
        raw = stripControlTags(raw);
        return legacyColors(raw);
    }

    public boolean condition(Player player, ScreenText text, Map<String, BiFunction<Player, ScreenText, String>> customPlaceholders) {
        String condition = text.condition();
        if (condition == null || condition.isBlank()) return true;
        String resolved = resolvePlaceholders(player, text, condition, customPlaceholders).trim();
        return !(resolved.isBlank() || resolved.equalsIgnoreCase("false") || resolved.equalsIgnoreCase("no") || resolved.equals("0"));
    }

    public String resolvePlaceholders(Player player, ScreenText text, String input, Map<String, BiFunction<Player, ScreenText, String>> customPlaceholders) {
        String out = input == null ? "" : input;
        if (player != null) {
            String world = player.getWorld() == null ? "" : player.getWorld().getName();
            String rank = rank(player);
            String money = moneyService == null ? "0" : moneyService.format(moneyService.balance(player.getUniqueId()));
            out = out
                .replace("%player_name%", player.getName())
                .replace("%ping%", player.getPing() + "ms")
                .replace("%world%", world)
                .replace("%player_world%", world)
                .replace("%3smpcore_world%", world)
                .replace("%3smpcore_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%3smpcore_rank%", rank)
                .replace("%3smpcore_rank_id%", rank)
                .replace("%3smpcore_money%", money)
                .replace("%3smpcore_money_formatted%", money)
                .replace("<rank_image>", rankImage(player));
        }
        out = out.replace("%3smpcore_max_players%", String.valueOf(Bukkit.getMaxPlayers()));
        for (Map.Entry<String, BiFunction<Player, ScreenText, String>> entry : customPlaceholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().apply(player, text);
            out = out.replace("%" + key + "%", value == null ? "" : value);
        }
        out = imageTags(out);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return imageTags(out);
    }

    private String applyStyle(String input, ScreenTextStyle style) {
        StringBuilder prefix = new StringBuilder();
        if (style.bold()) prefix.append("&l");
        if (style.italic()) prefix.append("&o");
        if (style.underline()) prefix.append("&n");
        if (!style.gradient().isBlank()) return "{grad:" + style.gradient() + "}" + prefix + input + "{/grad}";
        if (!style.color().isBlank()) prefix.insert(0, "{color:" + style.color() + "}");
        return prefix + input;
    }

    private String applyAnimationRaw(String input, ScreenTextAnimation animation, long ageMillis) {
        if (animation == null || !animation.animated()) return input;
        long activeAge = ageMillis - animation.delayMillis();
        if (activeAge <= 0L) return animation.type() == ScreenTextAnimation.Type.TYPEWRITER ? "" : input;
        double progress = Math.min(1.0D, (double) activeAge / (double) Math.max(1L, animation.durationMillis()));
        progress = ease(progress, animation.easing());
        return switch (animation.type()) {
            case TYPEWRITER -> {
                String clean = input.replaceAll("\\{grad:[^}]+}", "").replace("{/grad}", "").replaceAll("\\{color:#[A-Fa-f0-9]{6}}", "");
                yield visibleSubstring(clean, (int) Math.ceil(visibleLength(clean) * progress));
            }
            case SLIDE_LEFT -> " ".repeat(Math.max(0, (int) Math.round((1.0D - progress) * 8.0D))) + input;
            case SLIDE_RIGHT -> " ".repeat(Math.max(0, (int) Math.round(progress * 4.0D))) + input;
            case PULSE -> progress < 0.5D ? "{grad:gold}" + input + "{/grad}" : "{grad:royal}" + input + "{/grad}";
            case GLOW -> "{grad:royal_gold}" + input + "{/grad}";
            default -> input;
        };
    }

    private double ease(double progress, ScreenTextAnimation.Easing easing) {
        return switch (easing) {
            case EASE_IN -> progress * progress;
            case EASE_OUT -> 1.0D - Math.pow(1.0D - progress, 2.0D);
            case LINEAR -> progress;
        };
    }

    private String wrap(String input, int maxWidth) {
        if (maxWidth <= 0) return input;
        List<String> out = new ArrayList<>();
        for (String line : input.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) {
                if (visibleLength(current + (current.isEmpty() ? "" : " ") + word) > maxWidth && !current.isEmpty()) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
            out.add(current.toString());
        }
        return String.join("\n", out);
    }

    private String applyAlignment(String input, TextAlignment alignment, int width) {
        if (width <= 0 || alignment == TextAlignment.LEFT) return input;
        List<String> lines = new ArrayList<>();
        for (String line : input.split("\n", -1)) {
            int spaces = Math.max(0, width - visibleLength(line));
            if (alignment == TextAlignment.CENTER) spaces /= 2;
            lines.add(" ".repeat(spaces) + line);
        }
        return String.join("\n", lines);
    }

    private String applyLineSpacing(String input, int lineSpacing) {
        if (lineSpacing <= 0 || !input.contains("\n")) return input;
        return input.replace("\n", "\n" + "\n".repeat(lineSpacing));
    }

    private String applyLetterSpacing(String input, int spacing) {
        if (spacing <= 0) return input;
        StringBuilder out = new StringBuilder();
        String pad = " ".repeat(spacing);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            out.append(c);
            if (c != '\n' && c != ' ' && i + 1 < input.length() && input.charAt(i + 1) != '\n') out.append(pad);
        }
        return out.toString();
    }

    private String imageTags(String input) {
        String out = input;
        for (String id : registry.imageIds()) {
            out = out.replace("<img:" + id + ">", registry.image(id));
        }
        return out;
    }

    private String renderGradientTags(String input) {
        String out = input;
        int guard = 0;
        while (guard++ < 64) {
            int start = out.lastIndexOf("{grad:");
            if (start < 0) break;
            int idEnd = out.indexOf('}', start);
            int close = out.indexOf("{/grad}", idEnd + 1);
            if (idEnd < 0 || close < 0) break;
            String id = out.substring(start + 6, idEnd).toLowerCase(Locale.ROOT);
            String body = out.substring(idEnd + 1, close);
            String rendered = gradient(body, registry.gradient(id));
            out = out.substring(0, start) + rendered + out.substring(close + 7);
        }
        return out.replace("{/grad}", "");
    }

    private String renderRainbow(String input, boolean enabled) {
        if (!input.contains("{rainbow}")) return input;
        if (!enabled) return input.replace("{rainbow}", "").replace("{/rainbow}", "");
        return input.replace("{rainbow}", "{grad:rainbow}").replace("{/rainbow}", "{/grad}");
    }

    private String stripControlTags(String input) {
        return input
            .replace("{shadow}", "")
            .replace("{/shadow}", "")
            .replace("{pulse}", "")
            .replace("{/pulse}", "")
            .replace("{wave}", "")
            .replace("{/wave}", "")
            .replace("{glow}", "")
            .replace("{/glow}", "")
            .replace("{rainbow}", "")
            .replace("{/rainbow}", "")
            .replaceAll("\\{color:#([A-Fa-f0-9]{6})}", "&#$1");
    }

    private String gradient(String text, String spec) {
        List<Color> colors = colors(spec);
        if (colors.isEmpty()) colors = colors(registry.gradient("default"));
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
                if ("lmnok".indexOf(code) >= 0) formats.append(COLOR).append(code);
                else if (code == 'r') formats.setLength(0);
                continue;
            }
            if (c == COLOR && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                if ("lmnok".indexOf(code) >= 0) formats.append(COLOR).append(code);
                else if (code == 'r') formats.setLength(0);
                continue;
            }
            Color color = interpolate(colors, visible == 1 ? 0.0D : (double) index / (double) (visible - 1));
            out.append(sectionHex(color)).append(formats).append(c);
            index++;
        }
        return out.toString();
    }

    private String legacyColors(String input) {
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
                out.append(COLOR).append(input.charAt(++i));
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private int visibleLength(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == COLOR) && i + 1 < text.length()) {
                if (text.charAt(i + 1) == '#' && i + 7 < text.length()) i += 7;
                else i++;
                continue;
            }
            if (c == '{') {
                int end = text.indexOf('}', i);
                if (end > i) {
                    i = end;
                    continue;
                }
            }
            count++;
        }
        return count;
    }

    private String visibleSubstring(String input, int visibleChars) {
        if (visibleChars <= 0) return "";
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            out.append(c);
            if (c == '{') {
                int end = input.indexOf('}', i);
                if (end > i) {
                    out.append(input, i + 1, end + 1);
                    i = end;
                    continue;
                }
            }
            if (++visible >= visibleChars) break;
        }
        return out.toString();
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

    private String sectionHex(Color color) {
        String hex = String.format("%06x", color.getRGB() & 0xFFFFFF);
        StringBuilder out = new StringBuilder(String.valueOf(COLOR)).append('x');
        for (char c : hex.toCharArray()) out.append(COLOR).append(c);
        return out.toString();
    }

    private ShadowColor shadow(ScreenTextStyle.Shadow raw) {
        if (raw == null || !raw.enabled()) return null;
        String hex = raw.color().replace("#", "");
        if (hex.length() != 6) return null;
        try {
            int rgb = Integer.parseInt(hex, 16);
            int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D, raw.alpha())) * 255.0D);
            return ShadowColor.shadowColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String rank(Player player) {
        if (plugin instanceof ThreeSMPCorePlugin core && core.visualManager() != null) return core.visualManager().style(player).id();
        return "default";
    }

    private String rankImage(Player player) {
        if (plugin instanceof ThreeSMPCorePlugin core && core.visualManager() != null) return core.visualManager().style(player).image();
        return "";
    }

    public record RenderedFrame(Component component, String legacyKey, List<String> legacyLines) {
    }
}
