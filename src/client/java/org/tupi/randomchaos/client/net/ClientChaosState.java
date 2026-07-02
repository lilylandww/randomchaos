package org.tupi.randomchaos.client.net;

import java.util.UUID;

public final class ClientChaosState {
    public static final ClientChaosState INSTANCE = new ClientChaosState();

    public record Snapshot(
        long serverTick,
        long challengeStartTick,
        long challengeEndTick,
        long nextEventTick,
        String currentEventId,
        UUID currentVictimUuid,
        long currentEffectExpiryTick,
        long currentEffectStartTick,
        long intervalTicks,
        long clientGameTimeAtReceive
    ) {}

    private static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, "", null, 0, 0, 0, 0);

    private volatile Snapshot snapshot = EMPTY;

    private ClientChaosState() {}

    public void publish(Snapshot s) {
        snapshot = s;
    }

    public Snapshot snapshot() {
        return snapshot;
    }
}
