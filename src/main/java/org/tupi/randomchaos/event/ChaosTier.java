package org.tupi.randomchaos.event;

public enum ChaosTier {
    MINOR,
    MEDIUM,
    MAJOR;

    public boolean isHarmful() {
        return this == MEDIUM || this == MAJOR;
    }
}
