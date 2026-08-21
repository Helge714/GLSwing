package com.vingame.glswing.input;

/**
 * Marker for retained ancestors that receive wheel input before descendants.
 * Consuming the event prevents normal child-to-parent wheel dispatch.
 */
public interface GLMouseWheelCaptureTarget extends GLMouseTarget {
}
