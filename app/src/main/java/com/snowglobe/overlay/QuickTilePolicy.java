package com.snowglobe.overlay;

/** Pure state decisions. Permission is not proof that the overlay is running. */
public final class QuickTilePolicy {
    public enum Action { START, STOP, OPEN_SETUP }
    private QuickTilePolicy() {}
    public static Action action(boolean permission, boolean active) {
        // Always allow the user to stop an existing effect, even after permission changes.
        if (active) return Action.STOP;
        return permission ? Action.START : Action.OPEN_SETUP;
    }
    public static String status(boolean permission, boolean active, String serviceState) {
        if (active) return "On";
        if (!permission) return "Finish setup";
        if ("starting".equals(serviceState)) return "Starting";
        if ("error".equals(serviceState)) return "Open app to retry";
        return "Off";
    }
}
