package com.vingame.glswing.ui;

import java.util.List;
import java.util.Objects;

/** Storage unit and permitted display units; concrete units belong to the application. */
public record GLUnitFormats<U extends GLUnit>(U stored, U initial, List<U> choices) {
    public GLUnitFormats {
        Objects.requireNonNull(stored); Objects.requireNonNull(initial);
        choices = List.copyOf(choices);
        if (!choices.contains(initial) || choices.stream().anyMatch(u -> !stored.isCompatible(u))
                || choices.stream().map(GLUnit::id).distinct().count() != choices.size())
            throw new IllegalArgumentException("Invalid unit choices");
    }
    public static <U extends GLUnit> GLUnitFormats<U> fixed(U unit) {
        return new GLUnitFormats<>(unit, unit, List.of(unit));
    }
}
