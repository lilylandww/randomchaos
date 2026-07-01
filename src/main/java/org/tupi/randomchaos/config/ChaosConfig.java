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

    public int intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    public double effectCapRatio = DEFAULT_EFFECT_CAP_RATIO;

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
            writeDefaults(defaults);
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

        if (loaded.intervalSeconds <= 0 || loaded.intervalSeconds > MAX_INTERVAL_SECONDS) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: intervalSeconds must be in (0, {}], clamping to {}", MAX_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS);
            loaded.intervalSeconds = DEFAULT_INTERVAL_SECONDS;
        }
        if (!Double.isFinite(loaded.effectCapRatio) || loaded.effectCapRatio <= 0.0 || loaded.effectCapRatio > 1.0) {
            RandomChaosMod.LOGGER.warn("randomchaos.json: effectCapRatio must be a finite value in (0.0, 1.0], clamping to {}", DEFAULT_EFFECT_CAP_RATIO);
            loaded.effectCapRatio = DEFAULT_EFFECT_CAP_RATIO;
        }

        instance = loaded;
    }

    public static synchronized void reload() {
        load();
    }

    public int intervalTicks() {
        return intervalSeconds * 20;
    }

    public int effectCapTicks(int intervalTicks) {
        return (int) Math.round(intervalTicks * effectCapRatio);
    }

    private static void writeDefaults(ChaosConfig defaults) {
        Path parent = CONFIG_PATH.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            RandomChaosMod.LOGGER.error("Failed to create config directory", e);
            return;
        }
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults, writer);
        } catch (IOException e) {
            RandomChaosMod.LOGGER.error("Failed to write default randomchaos config", e);
        }
    }
}
