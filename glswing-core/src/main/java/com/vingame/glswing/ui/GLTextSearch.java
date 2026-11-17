package com.vingame.glswing.ui;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;

/** Shared matching engine for text, code and rendered document searches. */
final class GLTextSearch {
    record SearchMatch(int start,int end){}
    record SearchScan(List<SearchMatch> matches,String error){}
	static SearchScan scanMatches(String source, String query,
			GLTextSearchOptions options, BooleanSupplier cancelled) {
		if (query.isEmpty()) return new SearchScan(List.of(), null);
		Pattern pattern;
		try {
			int flags = options.matchCase() ? 0
					: Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
			pattern = Pattern.compile(options.regularExpression()
					? query : Pattern.quote(query), flags);
		} catch (PatternSyntaxException failure) {
			return new SearchScan(List.of(), failure.getDescription());
		}
		List<SearchMatch> result = new ArrayList<>();
		Matcher matcher = pattern.matcher(source);
		while (matcher.find()) {
			if (cancelled.getAsBoolean()) return new SearchScan(List.of(), null);
			if (matcher.start() == matcher.end()) continue;
			if (!options.wholeWord()
					|| wholeWord(source, matcher.start(), matcher.end()))
				result.add(new SearchMatch(matcher.start(), matcher.end()));
		}
		return new SearchScan(List.copyOf(result), null);
	}
	static boolean wholeWord(String source, int start, int end) {
		return (start == 0 || !isWordCharacter(source.codePointBefore(start)))
				&& (end == source.length()
						|| !isWordCharacter(source.codePointAt(end)));
	}
	static boolean isWordCharacter(int codePoint) {
		return Character.isLetterOrDigit(codePoint) || codePoint == '_';
	}
}
