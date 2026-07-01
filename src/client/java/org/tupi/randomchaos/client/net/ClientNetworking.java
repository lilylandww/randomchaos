package org.tupi.randomchaos.client.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.tupi.randomchaos.net.ChaosStatePayload;

public final class ClientNetworking {
    private ClientNetworking() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ChaosStatePayload.TYPE, (payload, context) -> {
            ClientChaosState s = ClientChaosState.INSTANCE;
            s.serverTick = payload.serverTick();
            s.challengeStartTick = payload.challengeStartTick();
            s.challengeEndTick = payload.challengeEndTick();
            s.nextEventTick = payload.nextEventTick();
            s.currentEventId = payload.currentEventId();
            s.currentVictimUuid = payload.currentVictimUuid();
            s.currentEffectExpiryTick = payload.currentEffectExpiryTick();
            net.minecraft.client.multiplayer.ClientLevel lvl = Minecraft.getInstance().level;
            s.clientGameTimeAtReceive = (lvl == null) ? 0L : lvl.getGameTime();
        });
    }
}
