package net.dark.threecore.license;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public final class LicenseManager {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] SECRET = "3SMPCore-License-Validation-Key-v1".getBytes(StandardCharsets.UTF_8);

    private final JavaPlugin plugin;
    private final File file;

    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "license/license.yml");
    }

    public boolean validate() {
        if (!file.exists()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
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
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                String template = "license:\n  enabled: false\n  owner: \"\"\n  key: \"\"\n  expires-at: \"\"\n  signature: \"\"\n";
                Files.writeString(file.toPath(), template, StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create license template", ex);
        }
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
}
