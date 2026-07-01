package org.tupi.randomchaos.client;

import net.fabricmc.api.ClientModInitializer;
import org.tupi.randomchaos.client.net.ClientNetworking;
import org.tupi.randomchaos.client.hud.ChaosHudOverlay;

public class RandomChaosClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientNetworking.register();
		ChaosHudOverlay.register();
	}
}
