package org.tupi.randomchaos.client.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.tupi.randomchaos.RandomChaosMod;
import org.tupi.randomchaos.client.net.ClientChaosState;

/**
 * Top-right HUD showing the two essential chaos timers as progress bars:
 *   1. Time until the next event fires
 *   2. Time remaining on the current active effect
 * Bars deplete (full -> empty) as time runs out.
 */
public final class ChaosHudOverlay {
    private ChaosHudOverlay() {}

    private static final int MARGIN = 4;
    private static final int BAR_WIDTH = 140;
    private static final int BAR_HEIGHT = 5;
    private static final int LINE_H = 10;
    private static final int GAP = 3;

    private static final int BAR_BG = 0x80000000;
    private static final int COL_HEADER = 0xFFCCCCCC;
    private static final int COL_NEXT = 0xFFFFAA00;
    private static final int COL_NOW = 0xFF55FFFF;
    private static final int COL_DIM = 0xFF888888;

    public static void register() {
        HudElementRegistry.addLast(RandomChaosMod.id("chaos_hud"), ChaosHudOverlay::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.font == null) {
            return;
        }

        ClientChaosState.Snapshot s = ClientChaosState.INSTANCE.snapshot();

        // No payload received yet -> render nothing.
        if (s.serverTick() == 0 && s.challengeStartTick() == 0) {
            return;
        }

        // Finished challenge summary.
        if (s.challengeEndTick() > 0) {
            long duration = s.challengeEndTick() - s.challengeStartTick();
            String line = "Chaos finished in " + fmtHMS(Math.max(0, duration));
            int screenW = g.guiWidth();
            g.text(mc.font, line, screenW - mc.font.width(line) - MARGIN, MARGIN, 0xFF55FF55, true);
            return;
        }

        // Challenge not started yet.
        if (s.challengeStartTick() == 0) {
            return;
        }

        // Reconstruct the live server tick from the last payload + local gameTime delta.
        long nowServer = s.serverTick() + (mc.level.getGameTime() - s.clientGameTimeAtReceive());
        if (nowServer < 0) {
            nowServer = 0;
        }

        int screenW = g.guiWidth();
        int right = screenW - MARGIN;
        int left = right - BAR_WIDTH;
        int y = MARGIN;

        // Header: elapsed challenge time.
        String header = "Chaos " + fmtHMS(Math.max(0, nowServer - s.challengeStartTick()));
        g.text(mc.font, header, right - mc.font.width(header), y, COL_HEADER, true);
        y += LINE_H + 2;

        // --- Bar 1: time until next event ---
        long interval = s.intervalTicks();
        long nextRemaining = Math.max(0, s.nextEventTick() - nowServer);
        double nextFrac = interval > 0 ? (double) nextRemaining / (double) interval : 0d;
        String nextLabel = "Next event";
        String nextCd = fmtMS(nextRemaining);
        g.text(mc.font, nextLabel, left, y, COL_NEXT, true);
        g.text(mc.font, nextCd, right - mc.font.width(nextCd), y, COL_NEXT, true);
        y += LINE_H;
        drawBar(g, left, y, BAR_WIDTH, nextFrac, COL_NEXT);
        y += BAR_HEIGHT + GAP + 2;

        // --- Bar 2: time remaining on the current effect ---
        boolean active = !s.currentEventId().isEmpty()
                && s.currentEffectExpiryTick() > 0
                && nowServer < s.currentEffectExpiryTick();
        if (active) {
            long total = s.currentEffectExpiryTick() - s.currentEffectStartTick();
            long nowRemaining = Math.max(0, s.currentEffectExpiryTick() - nowServer);
            double nowFrac = total > 0 ? (double) nowRemaining / (double) total : 0d;
            String label = humanize(s.currentEventId());
            String cd = fmtMS(nowRemaining);
            // Truncate the label so it never collides with the right-aligned countdown.
            int maxLabelW = BAR_WIDTH - mc.font.width(cd) - 6;
            label = fit(mc, label, maxLabelW);
            g.text(mc.font, label, left, y, COL_NOW, true);
            g.text(mc.font, cd, right - mc.font.width(cd), y, COL_NOW, true);
            y += LINE_H;
            drawBar(g, left, y, BAR_WIDTH, nowFrac, COL_NOW);
        } else {
            g.text(mc.font, "No active effect", left, y, COL_DIM, true);
        }
    }

    /**
     * Draws a horizontal progress bar: a translucent background spanning the full
     * width and a solid filled portion whose length is {@code fraction * width}.
     */
    private static void drawBar(GuiGraphicsExtractor g, int x, int y, int width, double fraction, int color) {
        double f = fraction;
        if (f < 0d) f = 0d;
        if (f > 1d) f = 1d;
        int fillW = (int) Math.round(width * f);
        g.fill(x, y, x + width, y + BAR_HEIGHT, BAR_BG);
        if (fillW > 0) {
            g.fill(x, y, x + fillW, y + BAR_HEIGHT, color);
        }
    }

    /** Trims {@code text} (appending "...") so its rendered width fits in {@code maxWidth}. */
    private static String fit(Minecraft mc, String text, int maxWidth) {
        if (mc.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellW = mc.font.width(ellipsis);
        if (maxWidth <= ellW) {
            return ellipsis;
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && mc.font.width(sb.toString()) + ellW > maxWidth) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append(ellipsis).toString();
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
}
