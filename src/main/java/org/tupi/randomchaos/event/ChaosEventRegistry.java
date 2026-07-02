package org.tupi.randomchaos.event;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChaosEventRegistry {
    public static final ChaosEventRegistry INSTANCE = new ChaosEventRegistry();
    private final Map<Identifier, ChaosEvent> byId = new HashMap<>();
    private final List<ChaosEvent> all = new ArrayList<>();
    private final Map<ChaosTier, List<ChaosEvent>> byTier = new EnumMap<>(ChaosTier.class);

    private ChaosEventRegistry() {
        for (ChaosTier tier : ChaosTier.values()) {
            byTier.put(tier, new ArrayList<>());
        }
    }

    public synchronized void register(ChaosEvent e) {
        if (byId.containsKey(e.id())) {
            throw new IllegalStateException("Chaos event already registered: " + e.id());
        }
        byId.put(e.id(), e);
        all.add(e);
        byTier.get(e.tier()).add(e);
    }

    public ChaosEvent get(Identifier id) {
        return byId.get(id);
    }

    public ChaosEvent pickRandom(RandomSource rng) {
        if (all.isEmpty()) {
            throw new IllegalStateException("no chaos events registered");
        }
        return all.get(rng.nextInt(all.size()));
    }

    public ChaosEvent pickRandom(RandomSource rng, ChaosTier tier) {
        List<ChaosEvent> pool = byTier.get(tier);
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("no chaos events registered for tier: " + tier);
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    public boolean hasTier(ChaosTier tier) {
        List<ChaosEvent> pool = byTier.get(tier);
        return pool != null && !pool.isEmpty();
    }

    public List<ChaosEvent> all() {
        return Collections.unmodifiableList(all);
    }

    public int size() {
        return all.size();
    }
}
