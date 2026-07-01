package org.tupi.randomchaos.client.net;

public final class ClientChaosState {
    public static final ClientChaosState INSTANCE = new ClientChaosState();

    private ClientChaosState() {}

    public volatile long serverTick;
    public volatile long challengeStartTick;
    public volatile long challengeEndTick;
    public volatile long nextEventTick;
    public volatile String currentEventId = "";
    public volatile java.util.UUID currentVictimUuid;
    public volatile long currentEffectExpiryTick;
    public volatile long clientGameTimeAtReceive;
}
