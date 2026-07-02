package org.tupi.randomchaos.event;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChaosEventRegistry {
    public static final ChaosEventRegistry INSTANCE = new ChaosEventRegistry();
    private final Map<Identifier, ChaosEvent> byId = new HashMap<>();
    private final List<ChaosEvent> all = new ArrayList<>();
    private final Map<ChaosTier, List<ChaosEvent>> byTier = new EnumMap<>(ChaosTier.class);
    private final Map<ChaosTier, Integer> cooldownSizes = new EnumMap<>(ChaosTier.class);
    private final Map<ChaosTier, Deque<Identifier>> recentPicks = new EnumMap<>(ChaosTier.class);

    private ChaosEventRegistry() {
        for (ChaosTier tier : ChaosTier.values()) {
            byTier.put(tier, new ArrayList<>());
            cooldownSizes.put(tier, 0);
            recentPicks.put(tier, new ArrayDeque<>());
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

    public void setCooldownSize(ChaosTier tier, int size) {
        cooldownSizes.put(tier, Math.max(0, size));
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
        Set<Identifier> recent = new HashSet<>(recentPicks.getOrDefault(tier, new ArrayDeque<>()));
        List<ChaosEvent> available = applyCooldown(pool, recent);
        ChaosEvent picked = available.get(rng.nextInt(available.size()));
        recordPick(tier, picked.id());
        return picked;
    }

    private void recordPick(ChaosTier tier, Identifier id) {
        int size = cooldownSizes.getOrDefault(tier, 0);
        if (size <= 0) return;
        Deque<Identifier> window = recentPicks.get(tier);
        if (window == null) {
            window = new ArrayDeque<>();
            recentPicks.put(tier, window);
        }
        window.addLast(id);
        while (window.size() > size) {
            window.removeFirst();
        }
    }

    public static List<ChaosEvent> applyCooldown(List<ChaosEvent> pool, Set<Identifier> recent) {
        if (recent.isEmpty()) {
            return new ArrayList<>(pool);
        }
        List<ChaosEvent> available = new ArrayList<>();
        for (ChaosEvent e : pool) {
            if (!recent.contains(e.id())) {
                available.add(e);
            }
        }
        return available.isEmpty() ? new ArrayList<>(pool) : available;
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
