package org.tupi.randomchaos.state;

import net.minecraft.server.MinecraftServer;

public final class ChaosStateManager {
	private ChaosStateManager() {
	}

	public static ChaosState get(MinecraftServer server) {
		return ChaosState.get(server);
	}
}
