package com.vingame.glswing.ui;

import java.util.List;

/** Stateless syntax lexer and editing conventions for a code language. */
public interface GLCodeLanguage {
	String id();
	List<GLCodeToken> tokenize(String source);
	default List<GLCodeDiagnostic> diagnose(String source) { return List.of(); }
	default String lineCommentPrefix() { return "//"; }
	/** Optional in-process code intelligence used when a document URI is configured. */
	default GLCodeDiagnosticProvider codeIntelligenceProvider() { return null; }
}
