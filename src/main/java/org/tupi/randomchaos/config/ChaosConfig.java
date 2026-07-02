package org.tupi.randomchaos.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import net.fabricmc.loader.api.FabricLoader;

import org.tupi.randomchaos.RandomChaosMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChaosConfig {
    public static final int DEFAULT_INTERVAL_SECONDS = 120;
    public static final double DEFAULT_EFFECT_CAP_RATIO = 0.7;
    private static final int MAX_INTERVAL_SECONDS = 86_400;

    public static final int DEFAULT_MAJOR_COOLDOWN_PICKS = 6;
    public static final int DEFAULT_MINOR_WEIGHT = 50;
    public static final int DEFAULT_MEDIUM_WEIGHT = 35;
    public static final int DEFAULT_MAJOR_WEIGHT = 40;
    public static final int DEFAULT_MINOR_COOLDOWN = 3;
    public static final int DEFAULT_MEDIUM_COOLDOWN = 4;

    public int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    public double effectCapRatio = DEFAULT_EFFECT_CAP_RATIO;
    public int majorCooldownPicks = DEFAULT_MAJOR_COOLDOWN_PICKS;
    public int minorWeight = DEFAULT_MINOR_WEIGHT;
    public int mediumWeight = DEFAULT_MEDIUM_WEIGHT;
    public int majorWeight = DEFAULT_MAJOR_WEIGHT;
    public int minorCooldown = DEFAULT_MINOR_COOLDOWN;
    public int mediumCooldown = DEFAULT_MEDIUM_COOLDOWN;

    private static volatile ChaosConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("randomchaos.json");

    private ChaosConfig() {
    }

    public static ChaosConfig get() {
        ChaosConfig local = instance;
        if (local == null) {
            synchronized (ChaosConfig.class) {
                local = instance;
                if (local == null) {
                    load();
                    local = instance;
                }
            }
        }
        return local;
    }

    public static synchronized void load() {
        if (!Files.exists(CONFIG_PATH)) {
            ChaosConfig defaults = new ChaosConfig();
            write(defaults);
            instance = defaults;
            return;
        }

        ChaosConfig loaded;
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            loaded = GSON.fromJson(reader, ChaosConfig.class);
        } catch (JsonSyntaxException | IOException e) {
            RandomChaosMod.LOGGER.error("Failed to load randomchaos config, using defaults", e);
            loaded = new ChaosConfig();
        }

        if (loaded == null) {
            loaded = new ChaosConfig();
        }

        loaded.validate();
        instance = loaded;
    }

    public static synchronized void reload() {
        load();
    }

    public static synchronized void save() {
        ChaosConfig local = get();
        local.validate();
        write(local);
    }

    private void validate() {
        if (intervalSeconds <= 0 || intervalSeconds > MAX_INTERVAL_SECONDS) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: intervalSeconds must be in (0, {}], clamping to {}", MAX_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS);
            intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        }
        if (!Double.isFinite(effectCapRatio) || effectCapRatio <= 0.0 || effectCapRatio > 1.0) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: effectCapRatio must be a finite value in (0.0, 1.0], clamping to {}", DEFAULT_EFFECT_CAP_RATIO);
            effectCapRatio = DEFAULT_EFFECT_CAP_RATIO;
        }
        if (majorCooldownPicks < 1) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: majorCooldownPicks must be >= 1, clamping to {}", DEFAULT_MAJOR_COOLDOWN_PICKS);
            majorCooldownPicks = DEFAULT_MAJOR_COOLDOWN_PICKS;
        }
        if (minorWeight < 0 || mediumWeight < 0 || majorWeight < 0
                || (minorWeight + mediumWeight + majorWeight == 0)) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: tier weights must be non-negative and not all zero, clamping to defaults");
            minorWeight = DEFAULT_MINOR_WEIGHT;
            mediumWeight = DEFAULT_MEDIUM_WEIGHT;
            majorWeight = DEFAULT_MAJOR_WEIGHT;
        }
        if (minorCooldown < 0) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: minorCooldown must be >= 0, clamping to {}", DEFAULT_MINOR_COOLDOWN);
            minorCooldown = DEFAULT_MINOR_COOLDOWN;
        }
        if (mediumCooldown < 0) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: mediumCooldown must be >= 0, clamping to {}", DEFAULT_MEDIUM_COOLDOWN);
            mediumCooldown = DEFAULT_MEDIUM_COOLDOWN;
        }
    }

    public int intervalTicks() {
        return intervalSeconds * 20;
    }

    public int effectCapTicks(int intervalTicks) {
        return (int) Math.round(intervalTicks * effectCapRatio);
    }

    private static void write(ChaosConfig config) {
        Path parent = CONFIG_PATH.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            RandomChaosMod.LOGGER.error("Failed to create config directory", e);
            return;
        }
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            RandomChaosMod.LOGGER.error("Failed to write randomchaos config", e);
        }
    }
}
