package org.tupi.randomchaos.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.tupi.randomchaos.client.net.ClientChaosState;
import org.tupi.randomchaos.RandomChaosMod;

public final class ChaosHudOverlay {
    private ChaosHudOverlay() {}

    public static void register() {
        HudElementRegistry.addLast(RandomChaosMod.id("chaos_hud"), ChaosHudOverlay::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        ClientChaosState.Snapshot s = ClientChaosState.INSTANCE.snapshot();

        long nowServer = s.serverTick() + (mc.level.getGameTime() - s.clientGameTimeAtReceive());
        if (nowServer < 0) {
            nowServer = 0;
        }

        String line1 = null;
        String line2 = null;
        String line3 = null;

        if (s.challengeEndTick() > 0) {
            long duration = s.challengeEndTick() - s.challengeStartTick();
            line1 = "FINISHED in " + fmtHMS(duration);
        } else if (s.challengeStartTick() == 0) {
            line1 = "Chaos  --:--:--";
        } else {
            line1 = "Chaos  " + fmtHMS(nowServer - s.challengeStartTick());
            line2 = "Next  " + fmtMS(Math.max(0, s.nextEventTick() - nowServer));
            if (!s.currentEventId().isEmpty() && nowServer < s.currentEffectExpiryTick()) {
                String event = humanize(s.currentEventId());
                String victim = victimName(s.currentVictimUuid());
                String remaining = fmtMS(s.currentEffectExpiryTick() - nowServer);
                line3 = "Now: " + event + " \u2192 " + victim + "  (" + remaining + ")";
            } else {
                line3 = "Now: \u2014";
            }
        }

        int maxW = 0;
        if (line1 != null) maxW = Math.max(maxW, mc.font.width(line1));
        if (line2 != null) maxW = Math.max(maxW, mc.font.width(line2));
        if (line3 != null) maxW = Math.max(maxW, mc.font.width(line3));

        int screenW = g.guiWidth();
        int x = screenW - maxW - 4;
        int y = 4;

        if (s.challengeEndTick() > 0) {
            g.text(mc.font, line1, x, y, 0x55FF55, true);
        } else if (s.challengeStartTick() == 0) {
            g.text(mc.font, line1, x, y, 0x808080, true);
        } else {
            g.text(mc.font, line1, x, y, 0xFFFFFF, true);
            y += 11;
            if (line2 != null) {
                g.text(mc.font, line2, x, y, 0xFFAA00, true);
                y += 11;
            }
            if (line3 != null) {
                g.text(mc.font, line3, x, y, 0x55FFFF, true);
            }
        }
    }

    private static String fmtHMS(long ticks) {
        long totalSeconds = ticks / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String fmtMS(long ticks) {
        long totalSeconds = ticks / 20;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static String humanize(String eventId) {
        int colonIndex = eventId.lastIndexOf(':');
        String name = (colonIndex >= 0) ? eventId.substring(colonIndex + 1) : eventId;
        return name.replace('_', ' ');
    }

    private static String victimName(java.util.UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "?";
        }
        var player = mc.level.getPlayerByUUID(uuid);
        if (player != null) {
            return player.getName().getString();
        }
        return "?";
    }
}
