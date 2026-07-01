package org.tupi.randomchaos;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tupi.randomchaos.command.RandomChaosCommand;
import org.tupi.randomchaos.config.ChaosConfig;
import org.tupi.randomchaos.event.ChaosEventRegistry;
import org.tupi.randomchaos.events.SpawnZombieEvent;
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

		ChaosEventRegistry.INSTANCE.register(new SpawnZombieEvent());

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
