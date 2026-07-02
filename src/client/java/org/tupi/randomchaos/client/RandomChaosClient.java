package org.tupi.randomchaos.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.tupi.randomchaos.client.net.ClientNetworking;
import org.tupi.randomchaos.client.hud.ChaosHudOverlay;
import org.tupi.randomchaos.net.ChaosStatePayload;

public class RandomChaosClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.clientboundPlay().register(ChaosStatePayload.TYPE, ChaosStatePayload.STREAM_CODEC);
		ClientNetworking.register();
		ChaosHudOverlay.register();
	}
}
