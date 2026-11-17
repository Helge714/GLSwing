package com.vingame.glswing.ui;

import java.util.List;

/** Stateless syntax lexer and editing conventions for a code language. */
public interface GLCodeLanguage {
	String id();
	List<GLCodeToken> tokenize(String source);
	default List<GLCodeDiagnostic> diagnose(String source) { return List.of(); }
	default String lineCommentPrefix() { return "//"; }
	/** Opt in to suggestions while typing identifiers, in addition to explicit completion. */
	default boolean supportsAutomaticCompletion() { return false; }
	/** Optional language-specific indentation for Enter; null uses the generic rules. */
	default String indentationAfter(String source, int offset, int width) { return null; }
	/** Optional language-specific formatter; null uses the generic formatter. */
	default String formatSource(String source, int width) { return null; }
	/** Whether the editor may offer its built-in document formatter. */
	default boolean supportsFormatting() { return true; }
	/** Whether line comment and uncomment commands are meaningful. */
	default boolean supportsLineComments() {
		String prefix = lineCommentPrefix();
		return prefix != null && !prefix.isBlank();
	}
	/** Whether an attached provider may organize imports for this language. */
	default boolean supportsOrganizeImports() { return false; }
	/** Whether an attached provider may navigate to symbol definitions. */
	default boolean supportsDefinitionNavigation() {
		return codeIntelligenceProvider() != null;
	}
	/** Whether the language may provide parameter or type inlay hints. */
	default boolean supportsInlayHints() {
		return codeIntelligenceProvider() != null;
	}
	/** Whether functional color-expression settings apply to this language. */
	default boolean supportsColorFormatting() { return false; }
	/** Optional in-process code intelligence used when a document URI is configured. */
	default GLCodeDiagnosticProvider codeIntelligenceProvider() { return null; }
}
