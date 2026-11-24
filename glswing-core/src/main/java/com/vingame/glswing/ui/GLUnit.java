package com.vingame.glswing.ui;

/** Application-defined numeric unit. Range conversion must be monotonic increasing. */
public interface GLUnit {
    String id();
    String displayName();
    String suffix();
    Object dimension();
    double toBase(double value);
    double fromBase(double value);
    default double preferredStep() { return .01; }
    default boolean isCompatible(GLUnit other) { return other != null && dimension().equals(other.dimension()); }
    default double convertTo(double value, GLUnit target) {
        if (!isCompatible(target)) throw new IllegalArgumentException("Incompatible units");
        return equals(target) ? value : target.fromBase(toBase(value));
    }
}
