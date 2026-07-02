package org.tupi.randomchaos.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;

public record DeferredAction(long fireAtTick, UUID victimUuid, String kind) {
    public static final String KIND_LIGHTNING = "lightning";
    public static final int MAX_QUEUE = 64;
    public static final long STALE_TICKS = 6000L;

    public static final Codec<DeferredAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("fire_at_tick").forGetter(DeferredAction::fireAtTick),
        UUIDUtil.CODEC.fieldOf("victim_uuid").forGetter(DeferredAction::victimUuid),
        Codec.STRING.fieldOf("kind").forGetter(DeferredAction::kind)
    ).apply(instance, DeferredAction::new));
}
