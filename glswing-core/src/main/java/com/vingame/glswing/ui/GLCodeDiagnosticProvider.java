package com.vingame.glswing.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Optional asynchronous code intelligence source, such as a language server. */
public interface GLCodeDiagnosticProvider {
	/** Opens a document and starts delivering complete diagnostic snapshots. */
	Session open(GLCodeDocument document, Consumer<Result> listener);

	/** One versioned diagnostic publication. */
	record Result(int version, List<GLCodeDiagnostic> diagnostics) {
		public Result {
			if (version < 0 || diagnostics == null)
				throw new IllegalArgumentException("Invalid diagnostic result");
			diagnostics = List.copyOf(diagnostics);
		}
	}

	/** Per-document binding. Providers may be shared by multiple editors. */
	interface Session extends AutoCloseable {
		void update(GLCodeDocument document);
		/** Requests completion proposals at a UTF-16 source offset. */
		default CompletionStage<List<GLCodeCompletion>> complete(GLCodeDocument document,
				int offset, Character trigger) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Requests document-wide import organization when supported. */
		default CompletionStage<List<GLCodeTextEdit>> organizeImports(
				GLCodeDocument document) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Requests callable signatures at a UTF-16 source offset. */
		default CompletionStage<List<GLCodeSignature>> signatureHelp(
				GLCodeDocument document, int offset, Character trigger) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Resolves definitions for the symbol at a UTF-16 source offset. */
		default CompletionStage<List<GLCodeLocation>> definitions(
				GLCodeDocument document, int offset) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Resolves implementations for the symbol at a UTF-16 source offset. */
		default CompletionStage<List<GLCodeLocation>> implementations(
				GLCodeDocument document, int offset) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Reports whether a call hierarchy can be opened at a source offset. */
		default CompletionStage<Boolean> canOpenCallHierarchy(
				GLCodeDocument document, int offset) {
			return CompletableFuture.completedFuture(false);
		}
		/** Resolves the first level of the call hierarchy at a source offset. */
		default CompletionStage<Optional<GLCodeCallHierarchy>> callHierarchy(
				GLCodeDocument document, int offset) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		/** Requests semantic classifications for the complete document. */
		default CompletionStage<List<GLCodeSemanticToken>> semanticTokens(
				GLCodeDocument document) {
			return CompletableFuture.completedFuture(List.of());
		}
		/** Requests hover information at a UTF-16 source offset. */
		default CompletionStage<Optional<GLCodeHover>> hover(GLCodeDocument document,
				int offset) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		@Override void close();
	}
}
