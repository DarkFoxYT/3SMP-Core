package net.dark.threecore.screentext;

import net.dark.threecore.config.ConfigFiles;
import net.dark.threecore.money.MoneyService;
import net.dark.threecore.text.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class ScreenTextManager implements Listener {
    private final JavaPlugin plugin;
    private final ScreenTextRegistry registry;
    private final ScreenTextRenderer renderer;
    private final ScreenTextEditorGUI editor;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Map<String, BiFunction<Player, ScreenText, String>> customPlaceholders = new LinkedHashMap<>();
    private BukkitTask task;
    private long tick;

    public ScreenTextManager(JavaPlugin plugin, ConfigFiles configs, MoneyService moneyService) {
        this.plugin = plugin;
        this.registry = new ScreenTextRegistry(plugin, configs);
        this.renderer = new ScreenTextRenderer(plugin, registry, moneyService);
        this.editor = new ScreenTextEditorGUI(plugin, this, registry);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ScreenTextAPI.install(this);
    }

    public void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, registry.renderIntervalTicks());
    }

    public void reload() {
        registry.reload();
        start();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (Player player : Bukkit.getOnlinePlayers()) clear(player);
    }

    public void openEditor(Player player) {
        editor.open(player);
    }

    public ScreenTextRegistry registry() {
        return registry;
    }

    public void registerPlaceholder(String id, BiFunction<Player, ScreenText, String> resolver) {
        if (id == null || id.isBlank() || resolver == null) return;
        customPlaceholders.put(id, resolver);
    }

    public void show(Player player, ScreenText text) {
        if (player == null || text == null) return;
        session(player).show(player, text);
    }

    public boolean show(Player player, String templateId) {
        ScreenText text = registry.template(templateId);
        if (text == null) return false;
        show(player, text);
        return true;
    }

    public void update(Player player, ScreenText text) {
        show(player, text);
    }

    public void remove(Player player, String id) {
        if (player == null || id == null) return;
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session != null) session.remove(player, id);
    }

    public void clear(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session != null) session.clear(player);
        player.clearTitle();
    }

    public void broadcast(ScreenText text) {
        for (Player player : Bukkit.getOnlinePlayers()) show(player, text);
    }

    public void broadcast(String templateId) {
        ScreenText text = registry.template(templateId);
        if (text != null) broadcast(text);
    }

    public void showToGroup(Collection<? extends Player> players, ScreenText text) {
        if (players == null) return;
        for (Player player : players) show(player, text);
    }

    public void showToGroup(Predicate<Player> predicate, ScreenText text) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (predicate.test(player)) show(player, text);
        }
    }

    public void showToPermission(String permission, ScreenText text) {
        showToGroup(player -> permission == null || permission.isBlank() || player.hasPermission(permission), text);
    }

    public void showFloating(Location location, ScreenText text) {
        showFloating(null, location, text);
    }

    public void showFloating(Player placeholderViewer, Location location, ScreenText text) {
        if (location == null || location.getWorld() == null || text == null) return;
        ScreenTextRenderer.RenderedFrame frame = renderer.render(placeholderViewer, text, 0L, customPlaceholders);
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(frame.component());
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
            entity.setShadowed(text.style().shadow().enabled());
        });
        long ticks = Math.max(1L, (text.durationMillis() <= 0L ? 5000L : text.durationMillis()) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isDead()) display.remove();
        }, ticks);
    }

    public int activeCount(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId());
        return session == null ? 0 : session.active.size();
    }

    private void tick() {
        tick++;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession session = sessions.get(player.getUniqueId());
            if (session != null) session.render(player);
        }
    }

    private PlayerSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerSession());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    private enum Channel {
        TITLE,
        ACTIONBAR,
        BOSSBAR
    }

    private Channel channel(ScreenText text) {
        if (text.position() == Position.ABSOLUTE) {
            if (text.y() <= 0.33D) return Channel.BOSSBAR;
            if (text.y() >= 0.66D) return Channel.ACTIONBAR;
            return Channel.TITLE;
        }
        if (text.position().top()) return Channel.BOSSBAR;
        if (text.position().bottom()) return Channel.ACTIONBAR;
        if (text.layer() == Layer.CRITICAL || text.layer() == Layer.OVERLAY || text.position() == Position.CENTER) return Channel.TITLE;
        return Channel.ACTIONBAR;
    }

    private final class PlayerSession {
        private final Map<String, ActiveText> active = new LinkedHashMap<>();

        void show(Player player, ScreenText text) {
            ActiveText previous = active.remove(text.id());
            if (previous != null) previous.hide(player);
            active.put(text.id(), new ActiveText(text));
        }

        void remove(Player player, String id) {
            ActiveText removed = active.remove(id);
            if (removed != null) removed.hide(player);
        }

        void clear(Player player) {
            for (ActiveText text : active.values()) text.hide(player);
            active.clear();
        }

        void render(Player player) {
            long now = System.currentTimeMillis();
            Iterator<ActiveText> iterator = active.values().iterator();
            while (iterator.hasNext()) {
                ActiveText text = iterator.next();
                if (text.expired(now)) {
                    text.hide(player);
                    iterator.remove();
                }
            }
            List<ActiveText> visible = active.values().stream()
                .filter(text -> renderer.condition(player, text.text, customPlaceholders))
                .sorted(Comparator
                    .comparingInt((ActiveText text) -> text.text.layer().ordinal())
                    .thenComparingInt(text -> text.text.priority())
                    .thenComparingInt(text -> text.text.zIndex()))
                .toList();
            renderBossBars(player, visible, now);
            renderActionBar(player, visible, now);
            renderTitle(player, visible, now);
        }

        private void renderBossBars(Player player, List<ActiveText> visible, long now) {
            List<String> visibleBossBars = new ArrayList<>();
            for (ActiveText activeText : visible) {
                if (channel(activeText.text) != Channel.BOSSBAR) continue;
                visibleBossBars.add(activeText.text.id());
                ScreenTextRenderer.RenderedFrame frame = activeText.render(player, now);
                if (activeText.bossBar == null) {
                    activeText.bossBar = BossBar.bossBar(frame.component(), 1.0F, bossColor(activeText.text.layer()), BossBar.Overlay.PROGRESS);
                    player.showBossBar(activeText.bossBar);
                } else if (!frame.legacyKey().equals(activeText.lastBossKey)) {
                    activeText.bossBar.name(frame.component());
                }
                activeText.lastBossKey = frame.legacyKey();
            }
            for (ActiveText text : active.values()) {
                if (text.bossBar != null && !visibleBossBars.contains(text.text.id())) {
                    player.hideBossBar(text.bossBar);
                    text.bossBar = null;
                }
            }
        }

        private void renderActionBar(Player player, List<ActiveText> visible, long now) {
            List<Component> parts = new ArrayList<>();
            for (ActiveText activeText : visible) {
                if (channel(activeText.text) == Channel.ACTIONBAR) parts.add(activeText.render(player, now).component());
            }
            if (parts.isEmpty()) return;
            Component output = Component.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) output = output.append(Component.text("   "));
                output = output.append(parts.get(i));
            }
            player.sendActionBar(output);
        }

        private void renderTitle(Player player, List<ActiveText> visible, long now) {
            ActiveText selected = null;
            for (ActiveText activeText : visible) {
                if (channel(activeText.text) == Channel.TITLE) selected = activeText;
            }
            if (selected == null) return;
            ScreenTextRenderer.RenderedFrame frame = selected.render(player, now);
            if (!selected.text.animation().animated() && frame.legacyKey().equals(selected.lastTitleKey)) return;
            List<String> lines = frame.legacyLines();
            Component title = lines.isEmpty() ? frame.component() : renderer.render(player, selected.text.toBuilder().content(lines.get(0)).build(), now - selected.startedAt, customPlaceholders).component();
            Component subtitle = Component.empty();
            if (lines.size() > 1) {
                String rest = String.join("\n", lines.subList(1, lines.size()));
                subtitle = renderer.render(player, selected.text.toBuilder().content(rest).build(), now - selected.startedAt, customPlaceholders).component();
            }
            long stay = selected.text.durationMillis() > 0L ? Math.min(selected.text.durationMillis(), 3500L) : 1600L;
            player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(120), Duration.ofMillis(stay), Duration.ofMillis(220))));
            selected.lastTitleKey = frame.legacyKey();
        }
    }

    private BossBar.Color bossColor(Layer layer) {
        return switch (layer) {
            case CRITICAL -> BossBar.Color.YELLOW;
            case OVERLAY, HUD_HIGH -> BossBar.Color.BLUE;
            default -> BossBar.Color.WHITE;
        };
    }

    private final class ActiveText {
        private final ScreenText text;
        private final long startedAt = System.currentTimeMillis();
        private long lastRenderTick = -1L;
        private ScreenTextRenderer.RenderedFrame lastFrame;
        private String lastTitleKey = "";
        private String lastBossKey = "";
        private BossBar bossBar;

        ActiveText(ScreenText text) {
            this.text = text;
        }

        boolean expired(long now) {
            return text.type() == ScreenTextType.TIMED && text.durationMillis() > 0L && now - startedAt >= text.durationMillis();
        }

        ScreenTextRenderer.RenderedFrame render(Player player, long now) {
            long interval = Math.max(1, text.refreshTicks());
            if (lastFrame != null && !text.animation().animated() && tick - lastRenderTick < interval) return lastFrame;
            lastRenderTick = tick;
            lastFrame = renderer.render(player, text, now - startedAt, customPlaceholders);
            return lastFrame;
        }

        void hide(Player player) {
            if (player != null && bossBar != null) player.hideBossBar(bossBar);
            bossBar = null;
        }
    }

    public void preview(Player player, String templateId) {
        if (!show(player, templateId)) Text.send(player, "<red>Unknown screen text template.</red>");
    }
}
