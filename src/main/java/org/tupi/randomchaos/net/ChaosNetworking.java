package org.tupi.randomchaos.net;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChaosNetworking {
    private ChaosNetworking() {}

    public static void sendTo(ServerPlayer player, ChaosStatePayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    public static void broadcast(MinecraftServer server, ChaosStatePayload payload) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendTo(p, payload);
        }
    }
}
