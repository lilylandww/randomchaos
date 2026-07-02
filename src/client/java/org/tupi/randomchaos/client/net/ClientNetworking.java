package org.tupi.randomchaos.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import org.tupi.randomchaos.net.ChaosStatePayload;

public final class ClientNetworking {
    private ClientNetworking() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ChaosStatePayload.TYPE, (payload, context) -> {
            ClientLevel lvl = Minecraft.getInstance().level;
            long gameTime = (lvl == null) ? 0L : lvl.getGameTime();
            ClientChaosState.INSTANCE.publish(new ClientChaosState.Snapshot(
                payload.serverTick(),
                payload.challengeStartTick(),
                payload.challengeEndTick(),
                payload.nextEventTick(),
                payload.currentEventId(),
                payload.currentVictimUuid(),
                payload.currentEffectExpiryTick(),
                payload.currentEffectStartTick(),
                payload.intervalTicks(),
                gameTime
            ));
        });
    }
}
