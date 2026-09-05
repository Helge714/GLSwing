package com.vingame.glswing.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Numeric formatting policy for theme-compatible functional color expressions.
 * Color model selection remains independent; this policy controls how bounded
 * channels and alpha are represented and how decimal values are rounded.
 */
public record GLColorExpressionFormat(ChannelNotation channelNotation,
        int fractionDigits, boolean trimTrailingZeros) {

    /** Representation used for channels whose logical range is zero to one. */
    public enum ChannelNotation {
        /** Integer values from 0 through 255. */
        BYTE,
        /** Normalized values with an explicit {@code f} suffix. */
        NORMALIZED_FLOAT,
        /** Values from 0 through 100 followed by {@code %}. */
        PERCENT
    }

    /** Compact default retaining four meaningful fractional digits. */
    public static final GLColorExpressionFormat DEFAULT =
            new GLColorExpressionFormat(ChannelNotation.NORMALIZED_FLOAT, 4, true);

    public GLColorExpressionFormat {
        if (channelNotation == null)
            throw new IllegalArgumentException("Channel notation is required");
        if (fractionDigits < 0 || fractionDigits > 12)
            throw new IllegalArgumentException(
                    "Fraction digits must be between 0 and 12");
    }

    public GLColorExpressionFormat withChannelNotation(ChannelNotation value) {
        return new GLColorExpressionFormat(value, fractionDigits, trimTrailingZeros);
    }

    public GLColorExpressionFormat withFractionDigits(int value) {
        return new GLColorExpressionFormat(channelNotation, value, trimTrailingZeros);
    }

    public GLColorExpressionFormat withTrimTrailingZeros(boolean value) {
        return new GLColorExpressionFormat(channelNotation, fractionDigits, value);
    }

    String bounded(double value) {
        double clamped = Math.max(0, Math.min(1, value));
        return switch (channelNotation) {
        case BYTE -> Long.toString(Math.round(clamped * 255));
        case NORMALIZED_FLOAT -> decimal(clamped) + "f";
        case PERCENT -> decimal(clamped * 100) + "%";
        };
    }

    String decimal(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Color channel must be finite");
        BigDecimal rounded = BigDecimal.valueOf(value)
                .setScale(fractionDigits, RoundingMode.HALF_UP);
        if (trimTrailingZeros) rounded = rounded.stripTrailingZeros();
        String text = rounded.toPlainString();
        return "-0".equals(text) ? "0" : text;
    }
}
