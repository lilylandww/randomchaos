package org.tupi.randomchaos.events;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.event.ChaosEvent;
import org.tupi.randomchaos.event.ChaosTier;

public class RainbowRoadEvent implements ChaosEvent {
    private static final int DURATION_TICKS = 600;

    private static final Block[] WOOL = {
        Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
        Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL,
        Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
        Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
    };

    private final Map<UUID, State> states = new HashMap<>();

    private static final class State {
        BlockPos lastPos;
        int colorIdx;
    }

    @Override
    public Identifier id() {
        return RandomChaosMod.id("rainbow_road");
    }

    @Override
    public ChaosTier tier() {
        return ChaosTier.MEDIUM;
    }

    @Override
    public int defaultDurationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void apply(ServerPlayer victim) {
        states.put(victim.getUUID(), new State());
        RandomChaosMod.LOGGER.info("Rainbow road started for {}", victim.getName().getString());
    }

    @Override
    public void tick(ServerPlayer victim, long now) {
        State s = states.get(victim.getUUID());
        if (s == null) {
            s = new State();
            states.put(victim.getUUID(), s);
        }

        BlockPos target = victim.blockPosition().below();
        if (target.equals(s.lastPos)) {
            return;
        }

        ServerLevel level = victim.level();
        BlockState existing = level.getBlockState(target);
        if (!existing.isAir() && existing.getBlock() != Blocks.BEDROCK && level.getBlockEntity(target) == null) {
            Block wool = WOOL[s.colorIdx % WOOL.length];
            level.setBlock(target, wool.defaultBlockState(), 3);
            s.colorIdx++;
        }
        s.lastPos = target;
    }

    @Override
    public void onEnd(ServerPlayer victim) {
        states.remove(victim.getUUID());
    }
}
