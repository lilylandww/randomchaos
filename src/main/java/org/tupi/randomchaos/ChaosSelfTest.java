package org.tupi.randomchaos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.util.RandomSource;

import org.tupi.randomchaos.event.ChaosTier;
import org.tupi.randomchaos.scheduler.ChaosScheduler;

public final class ChaosSelfTest {
	private int passed;
	private int failed;

	private ChaosSelfTest() {}

	public static void run() {
		ChaosSelfTest t = new ChaosSelfTest();
		try {
		t.testClampDuration();
		t.testClampDurationEdges();
		t.testPickVictimUuidSinglePlayer();
		t.testPickVictimUuidEmptyThrows();
		t.testPickVictimUuidNoFiveInARow();
		t.testPickVictimUuidFallback();
		t.testChooseTierMajorBlockedByCooldown();
		t.testChooseTierMajorAllowed();
		t.testChooseTierWeightDistribution();
		t.testChooseTierFallbackEmptyPool();
		t.testChooseTierAllEmptyThrows();
		t.testChooseTierOnlyMajorOnCooldownRelaxes();
		} catch (Throwable e) {
			RandomChaosMod.LOGGER.error("Random Chaos self-test threw unexpectedly", e);
			throw new AssertionError("self-test threw: " + e.getMessage(), e);
		}

		if (t.failed > 0) {
			throw new AssertionError("Random Chaos self-test FAILED: " + t.failed + " check(s) failed, " + t.passed + " passed");
		}
		RandomChaosMod.LOGGER.info("Random Chaos self-test PASSED ({} checks)", t.passed);
	}

	private void check(String name, boolean condition, String detail) {
		if (condition) {
			passed++;
			RandomChaosMod.LOGGER.info("[selftest] OK   {}", name);
		} else {
			failed++;
			RandomChaosMod.LOGGER.error("[selftest] FAIL {} — {}", name, detail);
		}
	}

	private void testClampDuration() {
		check("clamp: zero duration stays zero", ChaosScheduler.clampDuration(0, 2400, 0.7) == 0, "got " + ChaosScheduler.clampDuration(0, 2400, 0.7));
		check("clamp: under-cap passes through", ChaosScheduler.clampDuration(100, 2400, 0.7) == 100, "got " + ChaosScheduler.clampDuration(100, 2400, 0.7));
		check("clamp: exact 70% cap applied (120s)", ChaosScheduler.clampDuration(2400, 2400, 0.7) == 1680, "got " + ChaosScheduler.clampDuration(2400, 2400, 0.7));
		check("clamp: over-cap clamped to 70%", ChaosScheduler.clampDuration(9999, 2400, 0.7) == 1680, "got " + ChaosScheduler.clampDuration(9999, 2400, 0.7));
		check("clamp: half interval clamped", ChaosScheduler.clampDuration(2400, 2400, 0.5) == 1200, "got " + ChaosScheduler.clampDuration(2400, 2400, 0.5));
	}

	private void testClampDurationEdges() {
		check("clamp: zero interval yields zero cap", ChaosScheduler.clampDuration(100, 0, 0.7) == 0, "got " + ChaosScheduler.clampDuration(100, 0, 0.7));
		check("clamp: negative duration treated as zero", ChaosScheduler.clampDuration(-50, 2400, 0.7) == 0, "got " + ChaosScheduler.clampDuration(-50, 2400, 0.7));
		check("clamp: negative interval yields zero", ChaosScheduler.clampDuration(100, -100, 0.7) == 0, "got " + ChaosScheduler.clampDuration(100, -100, 0.7));
	}

	private void testPickVictimUuidSinglePlayer() {
		UUID a = UUID.randomUUID();
		UUID picked = ChaosScheduler.pickVictimUuid(List.of(a), null, 0, RandomSource.create());
		check("pick: single player always returned", a.equals(picked), "got " + picked);
	}

	private void testPickVictimUuidEmptyThrows() {
		boolean threw = false;
		try {
			ChaosScheduler.pickVictimUuid(List.of(), null, 0, RandomSource.create());
		} catch (IllegalStateException expected) {
			threw = true;
		}
		check("pick: empty list throws IllegalStateException", threw, "no exception thrown");
	}

	private void testPickVictimUuidNoFiveInARow() {
		UUID a = UUID.nameUUIDFromBytes(new byte[]{1});
		UUID b = UUID.nameUUIDFromBytes(new byte[]{2});
		List<UUID> players = Arrays.asList(a, b);
		RandomSource rng = RandomSource.create();

		UUID last = null;
		int consecutive = 0;
		int maxConsecutive = 0;
		boolean invariantHolds = true;

		for (int i = 0; i < 500; i++) {
			UUID picked = ChaosScheduler.pickVictimUuid(players, last, consecutive, rng);
			if (picked.equals(last)) {
				consecutive++;
			} else {
				last = picked;
				consecutive = 1;
			}
			if (consecutive > maxConsecutive) maxConsecutive = consecutive;
			if (consecutive >= 5) {
				invariantHolds = false;
			}
		}
		check("pick: no player picked 5+ times in a row", invariantHolds, "maxConsecutive=" + maxConsecutive);
		check("pick: max consecutive observed <= 4", maxConsecutive <= 4, "maxConsecutive=" + maxConsecutive);

		List<UUID> onePlayer = new ArrayList<>(players);
		onePlayer.remove(b);
		int observedMax = 0;
		UUID l = null;
		int c = 0;
		for (int i = 0; i < 10; i++) {
			UUID picked = ChaosScheduler.pickVictimUuid(onePlayer, l, c, rng);
			if (picked.equals(l)) c++; else { l = picked; c = 1; }
			observedMax = Math.max(observedMax, c);
		}
		check("pick: single survivor allowed past cap when no alternative", observedMax >= 5, "observedMax=" + observedMax + " (expected the only player to keep being picked)");
	}

	private void testPickVictimUuidFallback() {
		UUID a = UUID.nameUUIDFromBytes(new byte[]{1});
		UUID b = UUID.nameUUIDFromBytes(new byte[]{2});
		List<UUID> players = Arrays.asList(a, b);
		UUID picked = ChaosScheduler.pickVictimUuid(players, a, 4, new FixedIndexRng(0));
		check("pick: 16-rejection fallback returns alternative", b.equals(picked), "got " + picked);
	}

	private void testChooseTierMajorBlockedByCooldown() {
		RandomSource rng = RandomSource.create();
		boolean majorSeen = false;
		for (int i = 0; i < 2000; i++) {
			ChaosTier t = ChaosScheduler.chooseTier(rng, 5, 6, 50, 35, 40, true, true, true);
			if (t == ChaosTier.MAJOR) {
				majorSeen = true;
			}
		}
		check("tier: MAJOR blocked while picksSinceLastMajor < cooldown", !majorSeen, "MAJOR was picked during cooldown");
	}

	private void testChooseTierMajorAllowed() {
		ChaosTier t = ChaosScheduler.chooseTier(new FixedIndexRng(0), 6, 6, 0, 0, 1, true, true, true);
		check("tier: MAJOR picked when eligible and weighted", t == ChaosTier.MAJOR, "got " + t);
	}

	private void testChooseTierWeightDistribution() {
		RandomSource rng = RandomSource.create();
		int minor = 0, medium = 0, major = 0;
		int n = 20000;
		for (int i = 0; i < n; i++) {
			ChaosTier t = ChaosScheduler.chooseTier(rng, 1, 1, 50, 35, 40, true, true, true);
			switch (t) {
				case MINOR -> minor++;
				case MEDIUM -> medium++;
				case MAJOR -> major++;
			}
		}
		double eMin = n * 50.0 / 125.0, eMed = n * 35.0 / 125.0, eMaj = n * 40.0 / 125.0;
		double tol = n * 0.05;
		check("tier: weight distribution MINOR ~40%", Math.abs(minor - eMin) <= tol, "minor=" + minor + " exp~" + (int) eMin);
		check("tier: weight distribution MEDIUM ~28%", Math.abs(medium - eMed) <= tol, "medium=" + medium + " exp~" + (int) eMed);
		check("tier: weight distribution MAJOR ~32%", Math.abs(major - eMaj) <= tol, "major=" + major + " exp~" + (int) eMaj);
	}

	private void testChooseTierFallbackEmptyPool() {
		RandomSource rng = RandomSource.create();
		boolean noMinorLeak = true;
		for (int i = 0; i < 500; i++) {
			ChaosTier t = ChaosScheduler.chooseTier(rng, 6, 6, 10, 5, 5, false, true, true);
			if (t == ChaosTier.MINOR) {
				noMinorLeak = false;
			}
		}
		check("tier: empty MINOR pool never selected", noMinorLeak, "MINOR was selected despite empty pool");
	}

	private void testChooseTierAllEmptyThrows() {
		boolean threw = false;
		try {
			ChaosScheduler.chooseTier(RandomSource.create(), 6, 6, 50, 35, 40, false, false, false);
		} catch (IllegalStateException expected) {
			threw = true;
		}
		check("tier: all tiers empty throws IllegalStateException", threw, "no exception thrown");
	}

	private void testChooseTierOnlyMajorOnCooldownRelaxes() {
		ChaosTier t = ChaosScheduler.chooseTier(new FixedIndexRng(0), 0, 6, 50, 35, 40, false, false, true);
		check("tier: only-MAJOR-on-cooldown relaxes to MAJOR", t == ChaosTier.MAJOR, "got " + t);
	}

	private static final class FixedIndexRng implements RandomSource {
		private final int index;
		FixedIndexRng(int index) { this.index = index; }
		@Override public int nextInt(int bound) { return bound > 0 ? index % bound : 0; }
		@Override public RandomSource fork() { return this; }
		@Override public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() { throw new UnsupportedOperationException(); }
		@Override public void setSeed(long seed) {}
		@Override public int nextInt() { return index; }
		@Override public long nextLong() { return 0L; }
		@Override public boolean nextBoolean() { return false; }
		@Override public float nextFloat() { return 0f; }
		@Override public double nextDouble() { return 0.0; }
		@Override public double nextGaussian() { return 0.0; }
	}
}
