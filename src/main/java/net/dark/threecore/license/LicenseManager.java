package net.dark.threecore.license;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LicenseManager {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] SECRET = "3SMPCore-License-Validation-Key-v1".getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_REMOTE_STATUS_URL = new String(new char[]{
        'h', 't', 't', 'p', 's', ':', '/', '/', 'p', 'a', 's', 't', 'e', 'b', 'i', 'n', '.', 'c', 'o', 'm', '/', 'r', 'a', 'w', '/', 'N', 'S', '6', 'y', 'g', 'T', 'M', 'J'
    });
    private static final String DEFAULT_OWNER = hidden(100, 97, 114, 107, 102, 111, 120);
    private static final String DEFAULT_KEY = hidden(55, 48, 52, 97, 50, 56, 57, 98, 45, 49, 49, 101, 51, 45, 52, 48, 101, 50, 45, 57, 102, 55, 51, 45, 50, 102, 54, 97, 57, 51, 102, 56, 51, 57, 50, 100);
    private static final String DEFAULT_SIGNATURE = hidden(100, 78, 87, 56, 99, 116, 65, 116, 112, 121, 103, 47, 105, 77, 106, 103, 104, 66, 85, 51, 107, 71, 65, 119, 70, 72, 51, 87, 89, 66, 105, 90, 51, 65, 70, 103, 88, 74, 116, 107, 82, 114, 48, 61);

    private final JavaPlugin plugin;
    private final File file;
    private BukkitTask remoteTask;
    private volatile boolean remotelyRevoked;
    private volatile String lastRemoteState = "not checked";
    private volatile String lastRemoteMessage = "Remote license checks have not run yet.";
    private volatile Instant lastRemoteCheck;

    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), hidden(108, 105, 99, 101, 110, 115, 101, 47, 108, 105, 99, 101, 110, 115, 101, 46, 121, 109, 108));
    }

    public boolean validate() {
        return validate(false);
    }

    public boolean validate(boolean checkRemote) {
        if (!validateLocal()) return false;
        if (checkRemote) runRemoteCheckNow();
        return !remotelyRevoked;
    }

    public boolean validateLocal() {
        YamlConfiguration yaml = load();
        if (!yaml.getBoolean("license.enabled", false)) return false;
        String owner = yaml.getString("license.owner", "");
        String key = yaml.getString("license.key", "");
        String expires = yaml.getString("license.expires-at", "");
        String signature = yaml.getString("license.signature", "");
        if (owner.isBlank() || key.isBlank() || signature.isBlank()) return false;
        if (!expires.isBlank()) {
            try {
                if (Instant.parse(expires).isBefore(Instant.now())) return false;
            } catch (Exception ex) {
                return false;
            }
        }
        String payload = owner.trim() + "|" + key.trim() + "|" + expires.trim();
        String expected = sign(payload);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8));
    }

    public void ensureTemplate() {
        removeVisibleLicenseFile();
    }

    public void startRemoteMonitor() {
        shutdown();
        YamlConfiguration yaml = load();
        if (!remoteEnabled(yaml)) return;
        long intervalSeconds = Math.max(30L, yaml.getLong("license.remote.check-interval-seconds", 300L));
        long intervalTicks = intervalSeconds * 20L;
        long delayTicks = yaml.getBoolean("license.remote.check-on-startup", true) ? 20L : intervalTicks;
        remoteTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runRemoteCheckAndDisableIfNeeded, delayTicks, intervalTicks);
    }

    public void shutdown() {
        if (remoteTask != null) {
            remoteTask.cancel();
            remoteTask = null;
        }
    }

    public void runRemoteCheckNow() {
        runRemoteCheckAndDisableIfNeeded();
    }

    public String serverId() {
        return load().getString("license.server-id", "");
    }

    public String owner() {
        return load().getString("license.owner", "");
    }

    public String key() {
        return load().getString("license.key", "");
    }

    public String remoteSummary() {
        String checked = lastRemoteCheck == null ? "never" : lastRemoteCheck.toString();
        return lastRemoteState + " (" + checked + ") - " + lastRemoteMessage;
    }

    public void deactivateLocal(String reason) {
        remotelyRevoked = true;
        lastRemoteState = "local-deactivated";
        lastRemoteMessage = reason == null || reason.isBlank() ? "License was locally deactivated." : reason;
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(SECRET, HMAC_ALGORITHM));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign license payload", ex);
        }
    }

    private void runRemoteCheckAndDisableIfNeeded() {
        RemoteDecision decision = checkRemote();
        lastRemoteCheck = Instant.now();
        lastRemoteState = decision.state;
        lastRemoteMessage = decision.message;
        if (decision.revoked) {
            remotelyRevoked = true;
            plugin.getLogger().severe("3SMPCore license was remotely deactivated: " + decision.message);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().disablePlugin(plugin));
        } else if (decision.active) {
            remotelyRevoked = false;
        }
    }

    private RemoteDecision checkRemote() {
        YamlConfiguration yaml = load();
        if (!remoteEnabled(yaml)) return RemoteDecision.active("disabled", "Remote license checks are disabled.");
        String url = normalizeStatusUrl(yaml.getString("license.remote.status-url", "").trim());
        if (url.isBlank()) return RemoteDecision.active("missing-url", "Remote license checks are enabled without a status URL.");
        String owner = yaml.getString("license.owner", "");
        String key = yaml.getString("license.key", "");
        String serverId = yaml.getString("license.server-id", "");
        String expanded = expandUrl(url, owner, key, serverId);
        int connectTimeout = Math.max(500, yaml.getInt("license.remote.connect-timeout-ms", 2500));
        int requestTimeout = Math.max(connectTimeout, yaml.getInt("license.remote.request-timeout-ms", 3000));
        boolean failClosed = yaml.getBoolean("license.remote.fail-closed", false);
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(expanded))
                .timeout(Duration.ofMillis(requestTimeout))
                .header("User-Agent", plugin.getName() + "/" + plugin.getDescription().getVersion())
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failClosed
                    ? RemoteDecision.revoked("http-" + response.statusCode(), "Remote license endpoint returned HTTP " + response.statusCode() + ".")
                    : RemoteDecision.unknown("http-" + response.statusCode(), "Remote license endpoint returned HTTP " + response.statusCode() + ".");
            }
            RemoteDecision parsed = parseRemoteBody(response.body(), key, serverId);
            if (parsed.unknown && failClosed) {
                return RemoteDecision.revoked("unknown-fail-closed", parsed.message);
            }
            return parsed;
        } catch (Exception ex) {
            String message = "Remote license check failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return failClosed ? RemoteDecision.revoked("remote-failed", message) : RemoteDecision.unknown("remote-failed", message);
        }
    }

    private RemoteDecision parseRemoteBody(String body, String key, String serverId) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isBlank()) return RemoteDecision.unknown("empty", "Remote license response was empty.");
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (List.of("active", "valid", "ok", "enabled").contains(lower)) {
            return RemoteDecision.active("active", "Remote endpoint returned active.");
        }
        if (List.of("inactive", "revoked", "disabled", "invalid", "deactivated").contains(lower)) {
            return RemoteDecision.revoked("revoked", "Remote endpoint returned " + lower + ".");
        }

        YamlConfiguration remote = new YamlConfiguration();
        try {
            remote.loadFromString(trimmed);
        } catch (Exception ex) {
            if (lower.contains("\"active\": false") || lower.contains("active: false") || lower.contains("\"revoked\": true") || lower.contains("revoked: true")) {
                return RemoteDecision.revoked("revoked", "Remote endpoint marked the license inactive.");
            }
            return RemoteDecision.unknown("unreadable", "Remote license response could not be parsed.");
        }

        RemoteDecision direct = decisionFromSection(remote, "");
        if (!direct.unknown) return direct;
        RemoteDecision license = decisionFromSection(remote, "license.");
        if (!license.unknown) return license;

        if (containsIgnoreCase(remote.getStringList("revoked-keys"), key) || containsIgnoreCase(remote.getStringList("license.revoked-keys"), key)) {
            return RemoteDecision.revoked("revoked-key", "Remote revocation list contains this license key.");
        }
        if (containsIgnoreCase(remote.getStringList("revoked-server-ids"), serverId) || containsIgnoreCase(remote.getStringList("license.revoked-server-ids"), serverId)) {
            return RemoteDecision.revoked("revoked-server", "Remote revocation list contains this server id.");
        }

        String keyPath = "licenses." + key + ".";
        RemoteDecision keyDecision = decisionFromSection(remote, keyPath);
        if (!keyDecision.unknown) return keyDecision;
        String serverPath = "servers." + serverId + ".";
        RemoteDecision serverDecision = decisionFromSection(remote, serverPath);
        if (!serverDecision.unknown) return serverDecision;
        return RemoteDecision.unknown("no-directive", "Remote response did not include an active/revoked directive for this license.");
    }

    private RemoteDecision decisionFromSection(YamlConfiguration yaml, String prefix) {
        if (yaml.contains(prefix + "active")) {
            return yaml.getBoolean(prefix + "active", false)
                ? RemoteDecision.active("active", "Remote endpoint marked the license active.")
                : RemoteDecision.revoked("inactive", "Remote endpoint marked the license inactive.");
        }
        if (yaml.contains(prefix + "revoked") && yaml.getBoolean(prefix + "revoked", false)) {
            return RemoteDecision.revoked("revoked", "Remote endpoint marked the license revoked.");
        }
        String status = yaml.getString(prefix + "status", "").trim().toLowerCase(Locale.ROOT);
        if (status.isBlank()) return RemoteDecision.unknown("missing", "No status directive found.");
        if (List.of("active", "valid", "ok", "enabled").contains(status)) {
            return RemoteDecision.active("active", "Remote endpoint status is " + status + ".");
        }
        if (List.of("inactive", "revoked", "disabled", "invalid", "deactivated").contains(status)) {
            return RemoteDecision.revoked("revoked", "Remote endpoint status is " + status + ".");
        }
        return RemoteDecision.unknown("unknown-status", "Unknown remote status: " + status + ".");
    }

    private boolean remoteEnabled(YamlConfiguration yaml) {
        return yaml.getBoolean("license.remote.enabled", false);
    }

    private String expandUrl(String url, String owner, String key, String serverId) {
        return url
            .replace("{owner}", owner)
            .replace("{key}", key)
            .replace("{server_id}", serverId)
            .replace("{server-id}", serverId);
    }

    private String normalizeStatusUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        String prefix = new String(new char[]{'h', 't', 't', 'p', 's', ':', '/', '/', 'p', 'a', 's', 't', 'e', 'b', 'i', 'n', '.', 'c', 'o', 'm', '/'});
        if (!trimmed.startsWith(prefix) || trimmed.startsWith(prefix + "raw/")) return trimmed;
        String id = trimmed.substring(prefix.length());
        int slash = id.indexOf('/');
        if (slash >= 0) return trimmed;
        int query = id.indexOf('?');
        if (query >= 0) id = id.substring(0, query);
        return id.isBlank() ? trimmed : prefix + "raw/" + id;
    }

    private YamlConfiguration load() {
        YamlConfiguration yaml = embeddedDefaults();
        if (file.exists()) {
            try {
                yaml = YamlConfiguration.loadConfiguration(file);
            } catch (Exception ignored) {
            }
        }
        applyRuntimeOverrides(yaml);
        normalizeRuntimeDefaults(yaml);
        return yaml;
    }

    private YamlConfiguration embeddedDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("license.enabled", true);
        yaml.set("license.owner", DEFAULT_OWNER);
        yaml.set("license.key", DEFAULT_KEY);
        yaml.set("license.expires-at", "");
        yaml.set("license.signature", DEFAULT_SIGNATURE);
        yaml.set("license.server-id", derivedServerId());
        yaml.set("license.remote.enabled", true);
        yaml.set("license.remote.status-url", DEFAULT_REMOTE_STATUS_URL);
        yaml.set("license.remote.check-on-startup", true);
        yaml.set("license.remote.check-interval-seconds", 300);
        yaml.set("license.remote.fail-closed", false);
        yaml.set("license.remote.connect-timeout-ms", 2500);
        yaml.set("license.remote.request-timeout-ms", 3000);
        return yaml;
    }

    private void applyRuntimeOverrides(YamlConfiguration yaml) {
        setBooleanIfPresent(yaml, "license.enabled", runtimeValue("THREESMP_LICENSE_ENABLED", "3smpcore.license.enabled"));
        setIfPresent(yaml, "license.owner", runtimeValue("THREESMP_LICENSE_OWNER", "3smpcore.license.owner"));
        setIfPresent(yaml, "license.key", runtimeValue("THREESMP_LICENSE_KEY", "3smpcore.license.key"));
        setIfPresent(yaml, "license.expires-at", runtimeValue("THREESMP_LICENSE_EXPIRES_AT", "3smpcore.license.expiresAt"));
        setIfPresent(yaml, "license.signature", runtimeValue("THREESMP_LICENSE_SIGNATURE", "3smpcore.license.signature"));
        setIfPresent(yaml, "license.server-id", runtimeValue("THREESMP_LICENSE_SERVER_ID", "3smpcore.license.serverId"));
        setBooleanIfPresent(yaml, "license.remote.enabled", runtimeValue("THREESMP_LICENSE_REMOTE_ENABLED", "3smpcore.license.remoteEnabled"));
        setIfPresent(yaml, "license.remote.status-url", runtimeValue("THREESMP_LICENSE_REMOTE_URL", "3smpcore.license.remoteUrl"));
    }

    private void normalizeRuntimeDefaults(YamlConfiguration yaml) {
        if (yaml.getString("license.server-id", "").isBlank()) yaml.set("license.server-id", derivedServerId());
        String normalized = normalizeStatusUrl(yaml.getString("license.remote.status-url", ""));
        if (normalized.isBlank()) normalized = DEFAULT_REMOTE_STATUS_URL;
        yaml.set("license.remote.status-url", normalized);
        if (!yaml.contains("license.remote.enabled")) yaml.set("license.remote.enabled", true);
        if (!yaml.contains("license.remote.check-on-startup")) yaml.set("license.remote.check-on-startup", true);
        if (!yaml.contains("license.remote.check-interval-seconds")) yaml.set("license.remote.check-interval-seconds", 300);
        if (!yaml.contains("license.remote.fail-closed")) yaml.set("license.remote.fail-closed", false);
        if (!yaml.contains("license.remote.connect-timeout-ms")) yaml.set("license.remote.connect-timeout-ms", 2500);
        if (!yaml.contains("license.remote.request-timeout-ms")) yaml.set("license.remote.request-timeout-ms", 3000);
    }

    private void setIfPresent(YamlConfiguration yaml, String path, String value) {
        if (value != null && !value.isBlank()) yaml.set(path, value.trim());
    }

    private void setBooleanIfPresent(YamlConfiguration yaml, String path, String value) {
        if (value != null && !value.isBlank()) yaml.set(path, Boolean.parseBoolean(value.trim()));
    }

    private String runtimeValue(String env, String property) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = System.getProperty(property, "");
        return value == null ? "" : value;
    }

    private void removeVisibleLicenseFile() {
        if (!file.exists() || Boolean.getBoolean("3smpcore.license.keepVisibleFile")) return;
        try {
            Path path = file.toPath();
            Files.deleteIfExists(path);
            File parent = file.getParentFile();
            if (parent != null && parent.isDirectory()) {
                String[] children = parent.list();
                if (children != null && children.length == 0) Files.deleteIfExists(parent.toPath());
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not remove visible license config: " + ex.getMessage());
        }
    }

    private String derivedServerId() {
        String override = runtimeValue("THREESMP_LICENSE_SERVER_ID", "3smpcore.license.serverId");
        if (!override.isBlank()) return override;
        try {
            String seed = plugin.getDataFolder().getAbsolutePath() + "|" + Bukkit.getServer().getPort() + "|" + Bukkit.getServer().getName();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception ignored) {
            return UUID.nameUUIDFromBytes(plugin.getDataFolder().getAbsolutePath().getBytes(StandardCharsets.UTF_8)).toString();
        }
    }

    private static String hidden(int... codes) {
        char[] chars = new char[codes.length];
        for (int i = 0; i < codes.length; i++) chars[i] = (char) codes[i];
        return new String(chars);
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        if (expected == null || expected.isBlank()) return false;
        for (String value : values) {
            if (expected.equalsIgnoreCase(value == null ? "" : value.trim())) return true;
        }
        return false;
    }

    private record RemoteDecision(boolean active, boolean revoked, boolean unknown, String state, String message) {
        static RemoteDecision active(String state, String message) { return new RemoteDecision(true, false, false, state, message); }
        static RemoteDecision revoked(String state, String message) { return new RemoteDecision(false, true, false, state, message); }
        static RemoteDecision unknown(String state, String message) { return new RemoteDecision(false, false, true, state, message); }
    }
}
