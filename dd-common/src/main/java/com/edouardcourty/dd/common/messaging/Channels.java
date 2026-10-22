package com.edouardcourty.dd.common.messaging;

public enum Channels {
    DIM_SWITCH("dd:dimension_switch"),
    ENTITY_TRANSFER("dd:entity_transfer"),
    ADVANCEMENT_TRANSFER("dd:advancements"),
    SWITCH_FAILED("dd:switch_failed");

    private final String channel;
    Channels(String channel) { this.channel = channel; }
    public String toString() { return channel; }
}
