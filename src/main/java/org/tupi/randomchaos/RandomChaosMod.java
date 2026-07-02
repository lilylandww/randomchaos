package org.tupi.randomchaos;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

import org.tupi.randomchaos.net.ChaosStatePayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tupi.randomchaos.command.RandomChaosCommand;
import org.tupi.randomchaos.config.ChaosConfig;
import org.tupi.randomchaos.event.ChaosEventRegistry;
import org.tupi.randomchaos.event.ChaosTier;
import org.tupi.randomchaos.events.BlindnessEvent;
import org.tupi.randomchaos.events.CobbleCageEvent;
import org.tupi.randomchaos.events.CraterEvent;
import org.tupi.randomchaos.events.DizzinessEvent;
import org.tupi.randomchaos.events.HungerDrainEvent;
import org.tupi.randomchaos.events.MiningFatigueEvent;
import org.tupi.randomchaos.events.RainbowRoadEvent;
import org.tupi.randomchaos.events.SpawnCreeperEvent;
import org.tupi.randomchaos.events.SpawnSpiderEvent;
import org.tupi.randomchaos.events.SpawnZombieEvent;
import org.tupi.randomchaos.events.TeleportToGroundEvent;
import org.tupi.randomchaos.events.ThunderStrikeEvent;
import org.tupi.randomchaos.events.AdventureModeEvent;
import org.tupi.randomchaos.events.SlownessEvent;
import org.tupi.randomchaos.events.ClayFillEvent;
import org.tupi.randomchaos.events.PhoenixPathEvent;
import org.tupi.randomchaos.events.LeavesOnBreakEvent;
import org.tupi.randomchaos.events.ButterFingersEvent;
import org.tupi.randomchaos.events.HotbarShuffleEvent;
import org.tupi.randomchaos.events.BeeSwarmEvent;
import org.tupi.randomchaos.events.HeavyShoesEvent;
import org.tupi.randomchaos.events.PumpkinHeadEvent;
import org.tupi.randomchaos.events.SuddenSpringEvent;
import org.tupi.randomchaos.events.TheGangsAllHereEvent;
import org.tupi.randomchaos.events.GravityShiftEvent;
import org.tupi.randomchaos.events.SugarRushEvent;
import org.tupi.randomchaos.events.CatEyesEvent;
import org.tupi.randomchaos.events.SnackTimeEvent;
import org.tupi.randomchaos.events.SpringStepEvent;
import org.tupi.randomchaos.lifecycle.ChaosLifecycle;
import org.tupi.randomchaos.scheduler.ChaosScheduler;

public class RandomChaosMod implements ModInitializer {
	public static final String MOD_ID = "randomchaos";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Random Chaos...");

		ChaosConfig.get();

		PayloadTypeRegistry.clientboundPlay().register(ChaosStatePayload.TYPE, ChaosStatePayload.STREAM_CODEC);
		ChaosEventRegistry registry = ChaosEventRegistry.INSTANCE;
		registry.register(new HungerDrainEvent());
		registry.register(new SpawnSpiderEvent());
		registry.register(new CobbleCageEvent());
		registry.register(new SpawnZombieEvent());
		registry.register(new MiningFatigueEvent());
		registry.register(new SpawnCreeperEvent());
		registry.register(new DizzinessEvent());
		registry.register(new RainbowRoadEvent());
		registry.register(new BlindnessEvent());
		registry.register(new TeleportToGroundEvent());
		registry.register(new ThunderStrikeEvent());
		registry.register(new CraterEvent());
		registry.register(new AdventureModeEvent());
		registry.register(new SlownessEvent());
		registry.register(new ClayFillEvent());
		registry.register(new PhoenixPathEvent());
		registry.register(new LeavesOnBreakEvent());
		registry.register(new ButterFingersEvent());
		registry.register(new HotbarShuffleEvent());
		registry.register(new BeeSwarmEvent());
		registry.register(new HeavyShoesEvent());
		registry.register(new PumpkinHeadEvent());
		registry.register(new SuddenSpringEvent());
		registry.register(new TheGangsAllHereEvent());
		registry.register(new GravityShiftEvent());
		registry.register(new SugarRushEvent());
		registry.register(new CatEyesEvent());
		registry.register(new SnackTimeEvent());
		registry.register(new SpringStepEvent());

		ChaosConfig cfg = ChaosConfig.get();
		registry.setCooldownSize(ChaosTier.MINOR, cfg.minorCooldown);
		registry.setCooldownSize(ChaosTier.MEDIUM, cfg.mediumCooldown);

		ServerTickEvents.END_SERVER_TICK.register(ChaosScheduler::tick);
		ChaosLifecycle.register();
		RandomChaosCommand.register();

		LOGGER.info("Random Chaos initialized. Events registered: {}", ChaosEventRegistry.INSTANCE.size());

		ChaosSelfTest.run();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
