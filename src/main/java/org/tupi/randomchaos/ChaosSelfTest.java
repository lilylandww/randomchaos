package org.tupi.randomchaos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import net.minecraft.util.RandomSource;

import org.tupi.randomchaos.scheduler.ChaosScheduler;

public final class ChaosSelfTest {
	private int passed;
	private int failed;

	private ChaosSelfTest() {}

	public static void run() {
		ChaosSelfTest t = new ChaosSelfTest();
		try {
			t.testClampDuration();
			t.testPickVictimUuidSinglePlayer();
			t.testPickVictimUuidEmptyThrows();
			t.testPickVictimUuidNoFiveInARow();
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
}
