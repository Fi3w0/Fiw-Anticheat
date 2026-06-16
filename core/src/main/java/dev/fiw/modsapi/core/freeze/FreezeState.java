package dev.fiw.modsapi.core.freeze;

/** Position a frozen player is snapped back to until verification completes. */
public record FreezeState(double x, double y, double z, float yaw, float pitch, long since) {}
