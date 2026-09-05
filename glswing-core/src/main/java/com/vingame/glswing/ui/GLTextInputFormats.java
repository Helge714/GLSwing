package com.vingame.glswing.ui;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Reusable filters for text that may be entered into a {@link GLTextEditor}. */
public final class GLTextInputFormats {

	public static final Predicate<String> ANY = text -> true;
	public static final Predicate<String> DECIMAL_NUMBER =
			GLTextInputFormats::isPartialDecimalNumber;
	public static final Predicate<String> INTEGER_NUMBER =
			GLTextInputFormats::isPartialIntegerNumber;

	private static final Pattern DECIMAL = Pattern.compile(
			"[+-]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d*))(?:[eE][+-]?\\d*)?");
	private static final Pattern INTEGER = Pattern.compile("[+-]?\\d*");

	private GLTextInputFormats() {
	}

	/**
	 * Accepts complete numbers and useful intermediate states such as {@code -},
	 * {@code 1,} or {@code 1e-}. Final validity remains the verifier's job.
	 */
	private static boolean isPartialDecimalNumber(String text) {
		if (text == null)
			return false;
		if (text.isEmpty() || text.equals("+") || text.equals("-"))
			return true;
		return DECIMAL.matcher(text.replace(',', '.')).matches();
	}

	/** Accepts complete integers and the intermediate sign-only states. */
	private static boolean isPartialIntegerNumber(String text) {
		return text != null && INTEGER.matcher(text).matches();
	}
}
