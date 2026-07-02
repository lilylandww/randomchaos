package org.tupi.randomchaos.net;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChaosStatePayload(
    long serverTick,
    long challengeStartTick,
    long challengeEndTick,
    long nextEventTick,
    String currentEventId,
    UUID currentVictimUuid,
    long currentEffectExpiryTick
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ChaosStatePayload> TYPE =
        new CustomPacketPayload.Type<>(org.tupi.randomchaos.RandomChaosMod.id("state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> NULLABLE_UUID =
        StreamCodec.of(
            (buf, uuid) -> {
                buf.writeBoolean(uuid != null);
                if (uuid != null) {
                    UUIDUtil.STREAM_CODEC.encode(buf, uuid);
                }
            },
            buf -> buf.readBoolean() ? UUIDUtil.STREAM_CODEC.decode(buf) : null
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ChaosStatePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.LONG,        ChaosStatePayload::serverTick,
            ByteBufCodecs.LONG,        ChaosStatePayload::challengeStartTick,
            ByteBufCodecs.LONG,        ChaosStatePayload::challengeEndTick,
            ByteBufCodecs.LONG,        ChaosStatePayload::nextEventTick,
            ByteBufCodecs.STRING_UTF8, ChaosStatePayload::currentEventId,
            NULLABLE_UUID,             ChaosStatePayload::currentVictimUuid,
            ByteBufCodecs.LONG,        ChaosStatePayload::currentEffectExpiryTick,
            ChaosStatePayload::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
