package com.vingame.glswing.lsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionCapabilities;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKindCapabilities;
import org.eclipse.lsp4j.CodeActionLiteralSupportCapabilities;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintCapabilities;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationCapabilities;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.SignatureHelpCapabilities;
import org.eclipse.lsp4j.SignatureHelpContext;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureHelpTriggerKind;
import org.eclipse.lsp4j.CallHierarchyCapabilities;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.SemanticTokensCapabilities;
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

import com.vingame.glswing.ui.GLCodeDiagnostic;
import com.vingame.glswing.ui.GLCodeAction;
import com.vingame.glswing.ui.GLCodeDiagnosticProvider;
import com.vingame.glswing.ui.GLCodeCall;
import com.vingame.glswing.ui.GLCodeCallHierarchy;
import com.vingame.glswing.ui.GLCodeCompletion;
import com.vingame.glswing.ui.GLCodeTextEdit;
import com.vingame.glswing.ui.GLCodeWorkspaceEdit;
import com.vingame.glswing.ui.GLCodeDocument;
import com.vingame.glswing.ui.GLCodeHover;
import com.vingame.glswing.ui.GLCodeInlayHint;
import com.vingame.glswing.ui.GLCodeLocation;
import com.vingame.glswing.ui.GLCodeReference;
import com.vingame.glswing.ui.GLCodeSignature;
import com.vingame.glswing.ui.GLCodeSemanticToken;

/** Process-backed LSP code-intelligence provider using standard input and output. */
public final class GLLanguageServer implements GLCodeDiagnosticProvider, AutoCloseable {
	@JsonSegment("java")
	private interface JavaProtocol {
		@JsonRequest java.util.concurrent.CompletableFuture<WorkspaceEdit> organizeImports(
				CodeActionParams params);
		@JsonRequest java.util.concurrent.CompletableFuture<String> classFileContents(
				TextDocumentIdentifier identifier);
	}
	private interface ExtendedLanguageServer extends LanguageServer, JavaProtocol { }
	private record OpenDocument(GLCodeDocument document, Consumer<Result> listener,
			boolean announced) { }
	private final Process process;
	private final ExtendedLanguageServer server;
	private final Future<?> listening;
	private final Thread errorForwarder;
	private final Map<URI, OpenDocument> documents = new ConcurrentHashMap<>();
	private final Map<URI, List<GLCodeDiagnostic>> latestDiagnostics = new ConcurrentHashMap<>();
	private final List<String> semanticTokenTypes;
	private final List<String> semanticTokenModifiers;
	private final boolean semanticTokensFullSupported;
	private final boolean inlayHintsSupported;
	private volatile boolean closed;

	/** Starts and initializes a language server in the supplied workspace. */
	public GLLanguageServer(List<String> command, Path workspace) throws IOException {
		this(command, workspace, Map.of());
	}

	/** Starts a language server with profile-specific initialization options. */
	public GLLanguageServer(List<String> command, Path workspace,
			Map<String, ?> initializationOptions) throws IOException {
		if (command == null || command.isEmpty() || command.stream().anyMatch(String::isBlank))
			throw new IllegalArgumentException("Language-server command must not be empty");
		ProcessBuilder builder = new ProcessBuilder(List.copyOf(command));
		if (workspace != null) builder.directory(workspace.toFile());
		process = builder.start();
		errorForwarder = new Thread(() -> forwardServerErrors(process),
				"glswing-language-server-stderr");
		errorForwarder.setDaemon(true);
		errorForwarder.start();
		Launcher<ExtendedLanguageServer> launcher = new Launcher.Builder<ExtendedLanguageServer>()
				.setLocalService(new Client()).setRemoteInterface(ExtendedLanguageServer.class)
				.setInput(process.getInputStream()).setOutput(process.getOutputStream()).create();
		server = launcher.getRemoteProxy();
		listening = launcher.startListening();
		InitializeParams initialize = new InitializeParams();
		ClientCapabilities capabilities = new ClientCapabilities();
		TextDocumentClientCapabilities textDocument = new TextDocumentClientCapabilities();
		textDocument.setCompletion(new CompletionCapabilities());
		textDocument.setSignatureHelp(new SignatureHelpCapabilities());
		DefinitionCapabilities definitions = new DefinitionCapabilities();
		definitions.setLinkSupport(true);
		textDocument.setDefinition(definitions);
		ImplementationCapabilities implementations = new ImplementationCapabilities();
		implementations.setLinkSupport(true);
		textDocument.setImplementation(implementations);
		textDocument.setReferences(new org.eclipse.lsp4j.ReferencesCapabilities());
		textDocument.setRename(new org.eclipse.lsp4j.RenameCapabilities());
		textDocument.setCallHierarchy(new CallHierarchyCapabilities());
		textDocument.setInlayHint(new InlayHintCapabilities(false));
		SemanticTokensCapabilities semanticTokens = new SemanticTokensCapabilities();
		SemanticTokensClientCapabilitiesRequests semanticRequests =
				new SemanticTokensClientCapabilitiesRequests();
		semanticRequests.setFull(true);
		semanticTokens.setRequests(semanticRequests);
		semanticTokens.setTokenTypes(List.of("namespace", "type", "class", "enum",
				"interface", "struct", "typeParameter", "parameter", "variable",
				"property", "enumMember", "event", "function", "method", "macro",
				"keyword", "modifier", "comment", "string", "number", "regexp",
				"operator", "decorator"));
		semanticTokens.setTokenModifiers(List.of("declaration", "definition",
				"readonly", "static", "deprecated", "abstract", "async",
				"modification", "documentation", "defaultLibrary"));
		semanticTokens.setFormats(List.of("relative"));
		semanticTokens.setOverlappingTokenSupport(false);
		semanticTokens.setMultilineTokenSupport(false);
		textDocument.setSemanticTokens(semanticTokens);
		textDocument.setCodeAction(new CodeActionCapabilities(
				new CodeActionLiteralSupportCapabilities(new CodeActionKindCapabilities(
						List.of("quickfix", "refactor", "source", "source.organizeImports"))), false));
		capabilities.setTextDocument(textDocument);
		initialize.setCapabilities(capabilities);
		initialize.setInitializationOptions(initializationOptions == null
				? Map.of() : Map.copyOf(initializationOptions));
		if (workspace != null) initialize.setRootUri(workspace.toUri().toString());
		var initialized = server.initialize(initialize).join();
		var semanticProvider = initialized.getCapabilities().getSemanticTokensProvider();
		semanticTokenTypes = semanticProvider == null || semanticProvider.getLegend() == null
				|| semanticProvider.getLegend().getTokenTypes() == null ? List.of()
						: List.copyOf(semanticProvider.getLegend().getTokenTypes());
		semanticTokenModifiers = semanticProvider == null || semanticProvider.getLegend() == null
				|| semanticProvider.getLegend().getTokenModifiers() == null ? List.of()
						: List.copyOf(semanticProvider.getLegend().getTokenModifiers());
		var semanticFull = semanticProvider == null ? null : semanticProvider.getFull();
		semanticTokensFullSupported = semanticFull != null
				&& (semanticFull.isRight() || Boolean.TRUE.equals(semanticFull.getLeft()));
		var inlayProvider = initialized.getCapabilities().getInlayHintProvider();
		inlayHintsSupported = inlayProvider != null
				&& (inlayProvider.isRight() || Boolean.TRUE.equals(inlayProvider.getLeft()));
		server.initialized(new org.eclipse.lsp4j.InitializedParams());
	}

	/** Loads source text exposed by JDT for a {@code jdt:} class-file URI. */
	public CompletionStage<String> classFileContents(URI uri) {
		if (closed) return java.util.concurrent.CompletableFuture.failedFuture(
				new IllegalStateException("Language server is closed"));
		return server.classFileContents(new TextDocumentIdentifier(uri.toString()))
				.orTimeout(8, TimeUnit.SECONDS);
	}

	public GLLanguageServer(String command, Path workspace) throws IOException {
		this(List.of(command), workspace);
	}

	@Override public Session open(GLCodeDocument document, Consumer<Result> listener) {
		Objects.requireNonNull(document, "document");
		Objects.requireNonNull(listener, "listener");
		if (closed) throw new IllegalStateException("Language server is closed");
		OpenDocument state = new OpenDocument(document, listener, true);
		if (documents.putIfAbsent(document.uri(), state) != null)
			throw new IllegalStateException("Document is already open: " + document.uri());
		server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(document.uri().toString(), document.languageId(),
						document.version(), document.text())));
		return new DocumentSession(document.uri(), true);
	}
	/** Opens a read-only server-backed source without sending a didOpen notification. */
	public Session view(GLCodeDocument document, Consumer<Result> listener) {
		Objects.requireNonNull(document, "document");
		Objects.requireNonNull(listener, "listener");
		if (closed) throw new IllegalStateException("Language server is closed");
		OpenDocument state = new OpenDocument(document, listener, false);
		boolean registered = documents.putIfAbsent(document.uri(), state) == null;
		return new DocumentSession(document.uri(), registered);
	}

	@Override public synchronized void close() {
		if (closed) return;
		closed = true;
		for (URI uri : List.copyOf(documents.keySet())) closeDocument(uri);
		try {
			server.shutdown().orTimeout(3, TimeUnit.SECONDS).exceptionally(failure -> null).join();
		} catch (RuntimeException ignored) { }
		try { server.exit(); } catch (RuntimeException ignored) { }
		listening.cancel(true);
		try { process.getOutputStream().close(); } catch (IOException ignored) { }
		try { process.getInputStream().close(); } catch (IOException ignored) { }
		process.destroy();
		try {
			if (!process.waitFor(2, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				process.waitFor(2, TimeUnit.SECONDS);
			}
			errorForwarder.join(500);
		} catch (InterruptedException interrupted) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
		}
	}

	private static void forwardServerErrors(Process process) {
		try (BufferedReader errors = new BufferedReader(new InputStreamReader(
				process.getErrorStream(), StandardCharsets.UTF_8))) {
			String pendingSpiFlyHeader = null;
			for (String line; (line = errors.readLine()) != null; ) {
				if (pendingSpiFlyHeader != null) {
					if (isSpiFlyProviderRegistration(line)) {
						pendingSpiFlyHeader = null;
						continue;
					}
					System.err.println(pendingSpiFlyHeader);
					pendingSpiFlyHeader = null;
				}
				if (isSpiFlyHeader(line)) pendingSpiFlyHeader = line;
				else System.err.println(line);
			}
			if (pendingSpiFlyHeader != null) System.err.println(pendingSpiFlyHeader);
		} catch (IOException failure) {
			if (process.isAlive()) System.err.println(
					"Could not read language-server diagnostics: " + failure.getMessage());
		}
	}

	static boolean isSpiFlyHeader(String line) {
		return line != null && line.contains("org.apache.aries.spifly.BaseActivator log");
	}

	static boolean isSpiFlyProviderRegistration(String line) {
		return line != null && line.contains("Registered provider ch.qos.logback.classic.spi."
						+ "LogbackServiceProvider of service org.slf4j.spi."
						+ "SLF4JServiceProvider in bundle ch.qos.logback.classic");
	}

	private void updateDocument(URI uri, GLCodeDocument document) {
		OpenDocument previous = documents.get(uri);
		if (previous == null) return;
		if (!uri.equals(document.uri()))
			throw new IllegalArgumentException("A document session cannot change its URI");
		documents.put(uri, new OpenDocument(document, previous.listener(), previous.announced()));
		if (!previous.announced()) return;
		VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(
				uri.toString(), document.version());
		server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(identifier,
				List.of(new TextDocumentContentChangeEvent(document.text()))));
	}

	private void closeDocument(URI uri) {
		latestDiagnostics.remove(uri);
		OpenDocument removed = documents.remove(uri);
		if (removed != null && removed.announced())
			server.getTextDocumentService().didClose(new DidCloseTextDocumentParams(
					new TextDocumentIdentifier(uri.toString())));
	}

	private final class DocumentSession implements Session {
		private final URI uri;
		private final boolean ownsRegistration;
		private boolean sessionClosed;
		private DocumentSession(URI uri, boolean ownsRegistration) {
			this.uri = uri;
			this.ownsRegistration = ownsRegistration;
		}
		@Override public void update(GLCodeDocument document) {
			if (!sessionClosed && ownsRegistration) updateDocument(uri, document);
		}
		@Override public CompletionStage<Optional<GLCodeHover>> hover(GLCodeDocument document,
				int offset) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
			HoverParams params = new HoverParams(new TextDocumentIdentifier(uri.toString()),
					position(document.text(), offset));
			return server.getTextDocumentService().hover(params)
					.thenApply(value -> convertHover(document.text(), offset, value));
		}
		@Override public CompletionStage<List<GLCodeCompletion>> complete(
				GLCodeDocument document, int offset, Character trigger) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			CompletionParams params = new CompletionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setPosition(position(document.text(), offset));
			CompletionContext context = new CompletionContext();
			context.setTriggerKind(trigger == null ? CompletionTriggerKind.Invoked
					: CompletionTriggerKind.TriggerCharacter);
			if (trigger != null) context.setTriggerCharacter(trigger.toString());
			params.setContext(context);
			return requestCompletions(params, trigger == null ? 5 : 1).thenApply(result -> {
				List<CompletionItem> items = result == null ? List.of()
						: result.isLeft() ? result.getLeft() : result.getRight().getItems();
				int fallbackStart = completionStart(document.text(), offset);
				return items.stream()
						.sorted(java.util.Comparator.comparing(item -> item.getSortText() == null
								? item.getLabel() : item.getSortText()))
						.map(item -> completion(document.text(), item, fallbackStart, offset))
						.toList();
			});
		}
		@Override public CompletionStage<List<GLCodeSignature>> signatureHelp(
				GLCodeDocument document, int offset, Character trigger) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			SignatureHelpParams params = new SignatureHelpParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setPosition(position(document.text(), offset));
			SignatureHelpContext context = new SignatureHelpContext();
			context.setTriggerKind(trigger == null ? SignatureHelpTriggerKind.Invoked
					: SignatureHelpTriggerKind.TriggerCharacter);
			if (trigger != null) context.setTriggerCharacter(trigger.toString());
			params.setContext(context);
			return requestSignatureHelp(params, 5).thenApply(help -> {
				if (help == null || help.getSignatures() == null) return List.of();
				int activeSignature = help.getActiveSignature() == null ? 0 : help.getActiveSignature();
				ArrayList<GLCodeSignature> result = new ArrayList<>();
				for (int index = 0; index < help.getSignatures().size(); index++) {
					var signature = help.getSignatures().get(index);
					int activeParameter = signature.getActiveParameter() != null
							? signature.getActiveParameter()
							: index == activeSignature && help.getActiveParameter() != null
									? help.getActiveParameter() : -1;
					List<String> parameters = signature.getParameters() == null ? List.of()
							: signature.getParameters().stream().map(parameter ->
									parameter.getLabel().isLeft() ? parameter.getLabel().getLeft()
											: signature.getLabel().substring(
													parameter.getLabel().getRight().getFirst(),
													parameter.getLabel().getRight().getSecond())).toList();
					String documentation = signature.getDocumentation() == null ? ""
							: signature.getDocumentation().isLeft()
									? signature.getDocumentation().getLeft()
									: signature.getDocumentation().getRight().getValue();
					result.add(new GLCodeSignature(signature.getLabel(),
							plainText(documentation), parameters, activeParameter));
				}
				return List.copyOf(result);
			});
		}
		@Override public CompletionStage<List<GLCodeLocation>> definitions(
				GLCodeDocument document, int offset) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			DefinitionParams params = new DefinitionParams(
					new TextDocumentIdentifier(uri.toString()), position(document.text(), offset));
			return requestDefinitions(params).thenApply(GLLanguageServer::locations);
		}
		@Override public CompletionStage<List<GLCodeLocation>> implementations(
				GLCodeDocument document, int offset) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			ImplementationParams params = new ImplementationParams(
					new TextDocumentIdentifier(uri.toString()),
					position(document.text(), offset));
			return server.getTextDocumentService().implementation(params)
					.orTimeout(4, TimeUnit.SECONDS)
					.thenApply(GLLanguageServer::locations);
		}
		@Override public CompletionStage<List<GLCodeReference>> references(
				GLCodeDocument document, int offset, boolean includeDeclaration) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			ReferenceParams params = new ReferenceParams(
					new TextDocumentIdentifier(uri.toString()),
					position(document.text(), offset), new ReferenceContext(includeDeclaration));
			return server.getTextDocumentService().references(params)
					.orTimeout(8, TimeUnit.SECONDS).thenApply(result -> result == null
							? List.of() : result.stream().map(value -> new GLCodeReference(
									URI.create(value.getUri()), value.getRange().getStart().getLine(),
									value.getRange().getStart().getCharacter(),
									value.getRange().getEnd().getLine(),
									value.getRange().getEnd().getCharacter())).toList());
		}
		@Override public CompletionStage<List<GLCodeWorkspaceEdit>> rename(
				GLCodeDocument document, int offset, String newName) {
			if (sessionClosed || !uri.equals(document.uri()) || newName == null
					|| newName.isBlank()) return java.util.concurrent.CompletableFuture
							.completedFuture(List.of());
			RenameParams params = new RenameParams(new TextDocumentIdentifier(uri.toString()),
					position(document.text(), offset), newName);
			return server.getTextDocumentService().rename(params).orTimeout(12, TimeUnit.SECONDS)
					.thenApply(GLLanguageServer::workspaceLineEdits);
		}
		@Override public CompletionStage<List<GLCodeAction>> codeActions(
				GLCodeDocument document, int start, int end) {
			if (sessionClosed || !uri.equals(document.uri())) return java.util.concurrent
					.CompletableFuture.completedFuture(List.of());
			CodeActionContext context = new CodeActionContext();
			context.setDiagnostics(latestDiagnostics.getOrDefault(uri, List.of()).stream()
					.map(value -> {
						Diagnostic diagnostic = new Diagnostic();
						diagnostic.setRange(new org.eclipse.lsp4j.Range(
								position(document.text(), value.start()),
								position(document.text(), value.end())));
						diagnostic.setSeverity(value.severity()
								== GLCodeDiagnostic.Severity.ERROR
										? DiagnosticSeverity.Error : DiagnosticSeverity.Warning);
						diagnostic.setMessage(value.message());
						return diagnostic;
					}).toList());
			CodeActionParams params = new CodeActionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setRange(new org.eclipse.lsp4j.Range(position(document.text(), start),
					position(document.text(), end)));
			params.setContext(context);
			return server.getTextDocumentService().codeAction(params).orTimeout(8, TimeUnit.SECONDS)
					.thenApply(values -> {
						ArrayList<GLCodeAction> result = new ArrayList<>();
						if (values != null) for (var value : values) if (value.isRight()) {
							CodeAction action = value.getRight();
							if (action.getEdit() != null) result.add(new GLCodeAction(action.getTitle(),
									action.getKind() == null ? "" : action.getKind(),
									Boolean.TRUE.equals(action.getIsPreferred()),
									workspaceLineEdits(action.getEdit())));
						}
						return List.copyOf(result);
					});
		}
		@Override public CompletionStage<Optional<GLCodeCallHierarchy>> callHierarchy(
				GLCodeDocument document, int offset) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
			CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setPosition(position(document.text(), offset));
			return server.getTextDocumentService().prepareCallHierarchy(params)
					.orTimeout(4, TimeUnit.SECONDS).thenCompose(items -> {
						if (items == null || items.isEmpty()) return java.util.concurrent
								.CompletableFuture.completedFuture(Optional.empty());
						var root = items.stream().filter(
								GLLanguageServer::validCallHierarchyItem)
								.findFirst().orElse(null);
						if (root == null) return java.util.concurrent.CompletableFuture
								.completedFuture(Optional.empty());
						CallHierarchyIncomingCallsParams incomingParams =
								new CallHierarchyIncomingCallsParams(root);
						CallHierarchyOutgoingCallsParams outgoingParams =
								new CallHierarchyOutgoingCallsParams(root);
						var incoming = server.getTextDocumentService()
								.callHierarchyIncomingCalls(incomingParams)
								.orTimeout(4, TimeUnit.SECONDS)
								.exceptionally(failure -> List.of());
						var outgoing = server.getTextDocumentService()
								.callHierarchyOutgoingCalls(outgoingParams)
								.orTimeout(4, TimeUnit.SECONDS)
								.exceptionally(failure -> List.of());
						return incoming.thenCombine(outgoing, (in, out) -> Optional.of(
								new GLCodeCallHierarchy(root.getName(), location(root),
										in.stream().filter(call -> call != null
												&& validCallHierarchyItem(call.getFrom()))
												.map(call -> new GLCodeCall(
												call.getFrom().getName(), location(call.getFrom())))
												.toList(),
										out.stream().filter(call -> call != null
												&& validCallHierarchyItem(call.getTo()))
												.map(call -> new GLCodeCall(
												call.getTo().getName(), location(call.getTo())))
												.toList())));
					});
		}
		@Override public CompletionStage<Boolean> canOpenCallHierarchy(
				GLCodeDocument document, int offset) {
			if (sessionClosed || !uri.equals(document.uri()) || offset < 0
					|| offset > document.text().length())
				return java.util.concurrent.CompletableFuture.completedFuture(false);
			CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setPosition(position(document.text(), offset));
			return server.getTextDocumentService().prepareCallHierarchy(params)
					.orTimeout(4, TimeUnit.SECONDS)
					.thenApply(items -> items != null && items.stream()
							.anyMatch(GLLanguageServer::validCallHierarchyItem));
		}
		@Override public CompletionStage<List<GLCodeSemanticToken>> semanticTokens(
				GLCodeDocument document) {
			if (sessionClosed || !uri.equals(document.uri())
					|| !semanticTokensFullSupported || semanticTokenTypes.isEmpty())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			SemanticTokensParams params = new SemanticTokensParams(
					new TextDocumentIdentifier(uri.toString()));
			// JDT may briefly return an empty list while it is reconciling a didOpen
			// or didChange notification. Treat that as provisional for every document
			// version, not only during the first project import.
			return requestSemanticTokens(document, params, 8);
		}
		@Override public CompletionStage<List<GLCodeInlayHint>> inlayHints(
				GLCodeDocument document) {
			if (sessionClosed || !uri.equals(document.uri()) || !inlayHintsSupported)
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			InlayHintParams params = new InlayHintParams(
					new TextDocumentIdentifier(uri.toString()),
					new org.eclipse.lsp4j.Range(new Position(0, 0),
							position(document.text(), document.text().length())));
			return requestInlayHints(document, params, 6);
		}
		private CompletionStage<List<GLCodeInlayHint>> requestInlayHints(
				GLCodeDocument document, InlayHintParams params, int remainingAttempts) {
			OpenDocument active = documents.get(uri);
			if (sessionClosed || active == null
					|| active.document().version() != document.version())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			return server.getTextDocumentService().inlayHint(params)
					.orTimeout(30, TimeUnit.SECONDS)
					.thenApply(hints -> decodeInlayHints(document.text(), hints))
					.thenCompose(hints -> {
						if (!hints.isEmpty() || remainingAttempts <= 1)
							return java.util.concurrent.CompletableFuture.completedFuture(hints);
						return java.util.concurrent.CompletableFuture.supplyAsync(() -> null,
								java.util.concurrent.CompletableFuture.delayedExecutor(
										500L << (6 - remainingAttempts), TimeUnit.MILLISECONDS))
								.thenCompose(ignored -> requestInlayHints(document, params,
										remainingAttempts - 1));
					});
		}
		private CompletionStage<List<GLCodeSemanticToken>> requestSemanticTokens(
				GLCodeDocument document, SemanticTokensParams params,
				int remainingAttempts) {
			OpenDocument active = documents.get(uri);
			if (sessionClosed || active == null
					|| active.document().version() != document.version())
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			return server.getTextDocumentService().semanticTokensFull(params)
					// Initial project import can legitimately take longer than ordinary
					// interactive requests. This remains asynchronous and is cancelled by
					// the editor when its document version changes.
					.orTimeout(60, TimeUnit.SECONDS)
					.thenApply(tokens -> decodeSemanticTokens(document.text(), tokens,
							semanticTokenTypes, semanticTokenModifiers))
					.thenCompose(tokens -> {
						if (!tokens.isEmpty())
							return java.util.concurrent.CompletableFuture.completedFuture(tokens);
						OpenDocument current = documents.get(uri);
						if (remainingAttempts <= 1 || sessionClosed || document.text().isBlank()
								|| current == null || current.document().version() != document.version())
							return java.util.concurrent.CompletableFuture.completedFuture(List.of());
						long delay = switch (remainingAttempts) {
						case 8 -> 250; case 7 -> 500; case 6 -> 1_000;
						case 5 -> 2_000; case 4 -> 4_000; case 3 -> 8_000;
						default -> 16_000;
						};
						return java.util.concurrent.CompletableFuture.supplyAsync(() -> null,
								java.util.concurrent.CompletableFuture.delayedExecutor(
										delay, TimeUnit.MILLISECONDS))
								.thenCompose(ignored -> requestSemanticTokens(document, params,
										remainingAttempts - 1));
					});
		}
		@Override public CompletionStage<List<GLCodeTextEdit>> organizeImports(
				GLCodeDocument document) {
			if (sessionClosed || !uri.equals(document.uri()))
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			CodeActionContext context = new CodeActionContext();
			context.setDiagnostics(List.of());
			context.setOnly(List.of("source.organizeImports"));
			CodeActionParams params = new CodeActionParams();
			params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			params.setRange(new org.eclipse.lsp4j.Range(new Position(0, 0),
					position(document.text(), document.text().length())));
			params.setContext(context);
			if ("java".equals(document.languageId()))
				return server.organizeImports(params).handle((edit, failure) -> edit)
						.thenCompose(edit -> {
							List<GLCodeTextEdit> edits = edit == null ? List.of()
									: workspaceEdits(document.text(), uri, edit);
							if (!edits.isEmpty()) return java.util.concurrent.CompletableFuture
									.completedFuture(edits);
							return organizeImportsWithCodeAction(document, params)
									.thenCompose(fallback -> fallback.isEmpty()
											? completionImportEdits(document) : java.util.concurrent
													.CompletableFuture.completedFuture(fallback));
						});
			return organizeImportsWithCodeAction(document, params);
		}
		private CompletionStage<List<GLCodeTextEdit>> organizeImportsWithCodeAction(
				GLCodeDocument document, CodeActionParams params) {
			return server.getTextDocumentService().codeAction(params)
					.handle((actions, failure) -> actions).thenCompose(actions -> {
				for (var candidate : actions == null ? List.<org.eclipse.lsp4j.jsonrpc.messages.Either<
						org.eclipse.lsp4j.Command, CodeAction>>of() : actions) {
					CodeAction action = candidate.isRight() ? candidate.getRight() : null;
					if (action != null && action.getEdit() != null)
						return java.util.concurrent.CompletableFuture.completedFuture(
								workspaceEdits(document.text(), uri, action.getEdit()));
					var command = candidate.isLeft() ? candidate.getLeft()
							: action == null ? null : action.getCommand();
					if (command != null) {
						return executeWorkspaceEdit(document, command.getCommand(),
								command.getArguments());
					}
				}
				if ("java".equals(document.languageId()))
					return executeWorkspaceEdit(document, "java.edit.organizeImports",
							List.of(uri.toString()));
				return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			});
		}
		private CompletionStage<List<GLCodeTextEdit>> executeWorkspaceEdit(
				GLCodeDocument document, String command, List<Object> arguments) {
			ExecuteCommandParams execution = new ExecuteCommandParams(command, arguments);
			return server.getWorkspaceService().executeCommand(execution)
					.thenApply(result -> workspaceEdits(document.text(), uri,
							workspaceEdit(result)));
		}
		private CompletionStage<List<GLCodeTextEdit>> completionImportEdits(
				GLCodeDocument document) {
			List<java.util.concurrent.CompletableFuture<List<GLCodeCompletion>>> requests = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (GLCodeDiagnostic diagnostic : latestDiagnostics.getOrDefault(uri, List.of())) {
				int start = Math.max(0, Math.min(document.text().length(), diagnostic.start()));
				int end = Math.max(start, Math.min(document.text().length(), diagnostic.end()));
				String name = document.text().substring(start, end);
				if (name.isEmpty() || !Character.isUpperCase(name.codePointAt(0))
						|| !name.codePoints().allMatch(Character::isJavaIdentifierPart)) continue;
				names.add(name);
				requests.add(complete(document, end, null).toCompletableFuture());
			}
			if (requests.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(List.of());
			return java.util.concurrent.CompletableFuture.allOf(
					requests.toArray(java.util.concurrent.CompletableFuture[]::new)).thenApply(ignored -> {
				java.util.LinkedHashMap<String, GLCodeTextEdit> edits = new java.util.LinkedHashMap<>();
				for (int index = 0; index < requests.size(); index++) {
					String name = names.get(index);
					GLCodeCompletion chosen = requests.get(index).join().stream()
							.filter(value -> value.label().equals(name) && !value.additionalEdits().isEmpty())
							.sorted(java.util.Comparator.comparing(value ->
									value.detail().contains("java.util.") ? 0 : 1)).findFirst().orElse(null);
					if (chosen != null) for (GLCodeTextEdit edit : chosen.additionalEdits())
						edits.put(edit.start() + ":" + edit.end() + ":" + edit.text(), edit);
				}
				return List.copyOf(edits.values());
			});
		}
		@Override public void close() {
			if (sessionClosed) return;
			sessionClosed = true;
			if (ownsRegistration) closeDocument(uri);
		}
	}
	private static WorkspaceEdit workspaceEdit(Object value) {
		if (value instanceof WorkspaceEdit edit) return edit;
		if (value == null) return new WorkspaceEdit();
		return new com.google.gson.Gson().fromJson(
				new com.google.gson.Gson().toJsonTree(value), WorkspaceEdit.class);
	}
	private static List<GLCodeLocation> locations(
			org.eclipse.lsp4j.jsonrpc.messages.Either<
					List<? extends org.eclipse.lsp4j.Location>,
					List<? extends org.eclipse.lsp4j.LocationLink>> result) {
		if (result == null) return List.of();
		if (result.isLeft()) return result.getLeft().stream().map(location ->
				new GLCodeLocation(URI.create(location.getUri()),
						location.getRange().getStart().getLine(),
						location.getRange().getStart().getCharacter())).toList();
		return result.getRight().stream().map(link -> {
			var range = link.getTargetSelectionRange() == null
					? link.getTargetRange() : link.getTargetSelectionRange();
			return new GLCodeLocation(URI.create(link.getTargetUri()),
					range.getStart().getLine(), range.getStart().getCharacter());
		}).toList();
	}
	private static GLCodeLocation location(org.eclipse.lsp4j.CallHierarchyItem item) {
		var range = item.getSelectionRange() == null ? item.getRange()
				: item.getSelectionRange();
		return new GLCodeLocation(URI.create(item.getUri()),
				range.getStart().getLine(), range.getStart().getCharacter());
	}
	private static boolean validCallHierarchyItem(
			org.eclipse.lsp4j.CallHierarchyItem item) {
		return item != null && item.getName() != null && !item.getName().isBlank()
				&& item.getUri() != null && (item.getSelectionRange() != null
						|| item.getRange() != null);
	}
	static List<GLCodeSemanticToken> decodeSemanticTokens(String source,
			org.eclipse.lsp4j.SemanticTokens response, List<String> tokenTypes,
			List<String> tokenModifiers) {
		if (response == null || response.getData() == null) return List.of();
		List<Integer> data = response.getData();
		ArrayList<GLCodeSemanticToken> result = new ArrayList<>(data.size() / 5);
		int line = 0, character = 0;
		for (int index = 0; index + 4 < data.size(); index += 5) {
			int lineDelta = data.get(index);
			int characterDelta = data.get(index + 1);
			line += lineDelta;
			character = lineDelta == 0 ? character + characterDelta : characterDelta;
			int length = data.get(index + 2);
			int typeIndex = data.get(index + 3);
			if (length <= 0 || typeIndex < 0 || typeIndex >= tokenTypes.size())
				continue;
			int start = offset(source, line, character);
			int end = offset(source, line, character + length);
			if (end <= start) continue;
			long mask = Integer.toUnsignedLong(data.get(index + 4));
			java.util.LinkedHashSet<String> modifiers = new java.util.LinkedHashSet<>();
			for (int bit = 0; bit < tokenModifiers.size() && bit < 32; bit++)
				if ((mask & 1L << bit) != 0)
					modifiers.add(tokenModifiers.get(bit));
			result.add(new GLCodeSemanticToken(start, end,
					tokenTypes.get(typeIndex), modifiers));
		}
		return List.copyOf(result);
	}
	static List<GLCodeInlayHint> decodeInlayHints(String source,
			List<InlayHint> response) {
		if (response == null || response.isEmpty()) return List.of();
		ArrayList<GLCodeInlayHint> result = new ArrayList<>(response.size());
		for (InlayHint hint : response) {
			if (hint == null || hint.getPosition() == null || hint.getLabel() == null)
				continue;
			String label = hint.getLabel().isLeft() ? hint.getLabel().getLeft()
					: hint.getLabel().getRight().stream()
							.map(part -> part.getValue() == null ? "" : part.getValue())
							.collect(java.util.stream.Collectors.joining());
			if (label == null || label.isBlank()) continue;
			int at = offset(source, hint.getPosition().getLine(),
					hint.getPosition().getCharacter());
			GLCodeInlayHint.Kind kind = hint.getKind() == InlayHintKind.Parameter
					? GLCodeInlayHint.Kind.PARAMETER
					: hint.getKind() == InlayHintKind.Type
							? GLCodeInlayHint.Kind.TYPE : GLCodeInlayHint.Kind.OTHER;
			result.add(new GLCodeInlayHint(at, label, kind));
		}
		result.sort(java.util.Comparator.comparingInt(GLCodeInlayHint::offset));
		return List.copyOf(result);
	}
	private static List<GLCodeTextEdit> workspaceEdits(String source, URI uri,
			WorkspaceEdit workspaceEdit) {
		List<TextEdit> edits = workspaceEdit.getChanges() == null ? null
				: workspaceEdit.getChanges().get(uri.toString());
		if (edits == null && workspaceEdit.getDocumentChanges() != null)
			for (var change : workspaceEdit.getDocumentChanges())
				if (change.isLeft() && uri.toString().equals(
						change.getLeft().getTextDocument().getUri())) {
					edits = change.getLeft().getEdits().stream()
							.filter(org.eclipse.lsp4j.jsonrpc.messages.Either::isLeft)
							.map(org.eclipse.lsp4j.jsonrpc.messages.Either::getLeft).toList();
					break;
				}
		if (edits == null) return List.of();
		return edits.stream().map(edit -> new GLCodeTextEdit(
				offset(source, edit.getRange().getStart().getLine(),
						edit.getRange().getStart().getCharacter()),
				offset(source, edit.getRange().getEnd().getLine(),
						edit.getRange().getEnd().getCharacter()), edit.getNewText())).toList();
	}
	private static List<GLCodeWorkspaceEdit> workspaceLineEdits(WorkspaceEdit edit) {
		if (edit == null) return List.of();
		ArrayList<GLCodeWorkspaceEdit> result = new ArrayList<>();
		if (edit.getChanges() != null) edit.getChanges().forEach((target, edits) -> {
			URI targetUri = URI.create(target);
			for (TextEdit value : edits) result.add(lineEdit(targetUri, value));
		});
		if (edit.getDocumentChanges() != null) for (var change : edit.getDocumentChanges())
			if (change.isLeft()) {
				URI targetUri = URI.create(change.getLeft().getTextDocument().getUri());
				for (var value : change.getLeft().getEdits())
					if (value.isLeft()) result.add(lineEdit(targetUri, value.getLeft()));
			}
		return List.copyOf(result);
	}
	private static GLCodeWorkspaceEdit lineEdit(URI uri, TextEdit edit) {
		var start = edit.getRange().getStart();
		var end = edit.getRange().getEnd();
		return new GLCodeWorkspaceEdit(uri, start.getLine(), start.getCharacter(),
				end.getLine(), end.getCharacter(), edit.getNewText());
	}
	private static GLCodeCompletion completion(String source, CompletionItem item,
			int fallbackStart, int fallbackEnd) {
		int start = fallbackStart, end = fallbackEnd;
		String inserted = item.getInsertText() == null ? item.getLabel() : item.getInsertText();
		if (item.getTextEdit() != null) {
			TextEdit edit;
			if (item.getTextEdit().isLeft()) edit = item.getTextEdit().getLeft();
			else {
				var replacement = item.getTextEdit().getRight();
				edit = new TextEdit(replacement.getReplace(), replacement.getNewText());
			}
			start = offset(source, edit.getRange().getStart().getLine(),
					edit.getRange().getStart().getCharacter());
			end = offset(source, edit.getRange().getEnd().getLine(),
					edit.getRange().getEnd().getCharacter());
			inserted = edit.getNewText();
		}
		Snippet snippet = item.getInsertTextFormat() == InsertTextFormat.Snippet
				? plainSnippet(inserted) : new Snippet(inserted, inserted.length());
		List<GLCodeTextEdit> additional = item.getAdditionalTextEdits() == null ? List.of()
				: item.getAdditionalTextEdits().stream().map(edit -> new GLCodeTextEdit(
					offset(source, edit.getRange().getStart().getLine(),
							edit.getRange().getStart().getCharacter()),
					offset(source, edit.getRange().getEnd().getLine(),
							edit.getRange().getEnd().getCharacter()), edit.getNewText())).toList();
		return new GLCodeCompletion(item.getLabel(),
				item.getDetail() == null ? "" : item.getDetail(), snippet.text(),
				start, end, snippet.caret(), additional);
	}
	record Snippet(String text, int caret) { }
	static Snippet plainSnippet(String source) {
		StringBuilder text = new StringBuilder();
		int firstTabStop = Integer.MAX_VALUE, caret = -1;
		for (int index = 0; index < source.length();) {
			char value = source.charAt(index);
			if (value == '\\' && index + 1 < source.length()
					&& (source.charAt(index + 1) == '$' || source.charAt(index + 1) == '\\')) {
				text.append(source.charAt(index + 1)); index += 2; continue;
			}
			if (value != '$' || index + 1 >= source.length()) {
				text.append(value); index++; continue;
			}
			int cursor = index + 1, number = 0;
			boolean braced = cursor < source.length() && source.charAt(cursor) == '{';
			if (braced) cursor++;
			int digits = cursor;
			while (cursor < source.length() && Character.isDigit(source.charAt(cursor)))
				number = number * 10 + source.charAt(cursor++) - '0';
			if (cursor == digits) { text.append(value); index++; continue; }
			String defaultText = "";
			if (braced && cursor < source.length() && source.charAt(cursor) == ':') {
				int close = source.indexOf('}', ++cursor);
				if (close < 0) { text.append(value); index++; continue; }
				defaultText = source.substring(cursor, close); cursor = close;
			}
			if (braced && (cursor >= source.length() || source.charAt(cursor) != '}')) {
				text.append(value); index++; continue;
			}
			if (number == 0 && caret < 0 || number > 0 && number < firstTabStop) {
				caret = text.length();
				if (number > 0) firstTabStop = number;
			}
			text.append(defaultText);
			index = braced ? cursor + 1 : cursor;
		}
		return new Snippet(text.toString(), caret < 0 ? text.length() : caret);
	}
	private java.util.concurrent.CompletableFuture<
			org.eclipse.lsp4j.jsonrpc.messages.Either<List<CompletionItem>,
					org.eclipse.lsp4j.CompletionList>> requestCompletions(
					CompletionParams params, int remainingAttempts) {
		return server.getTextDocumentService().completion(params).thenCompose(result -> {
			boolean empty = result == null || result.isLeft() && result.getLeft().isEmpty()
					|| result.isRight() && result.getRight().getItems().isEmpty();
			if (!empty || remainingAttempts <= 1 || closed) return
					java.util.concurrent.CompletableFuture.completedFuture(result);
			long delay = switch (remainingAttempts) {
			case 5 -> 200; case 4 -> 500; case 3 -> 1_000; default -> 2_000;
			};
			return java.util.concurrent.CompletableFuture.supplyAsync(() -> null,
					java.util.concurrent.CompletableFuture.delayedExecutor(delay,
							TimeUnit.MILLISECONDS)).thenCompose(ignored ->
							requestCompletions(params, remainingAttempts - 1));
		});
	}
	private java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.SignatureHelp>
			requestSignatureHelp(SignatureHelpParams params, int remainingAttempts) {
		return server.getTextDocumentService().signatureHelp(params).thenCompose(result -> {
			boolean empty = result == null || result.getSignatures() == null
					|| result.getSignatures().isEmpty();
			if (!empty || remainingAttempts <= 1 || closed)
				return java.util.concurrent.CompletableFuture.completedFuture(result);
			long delay = switch (remainingAttempts) {
			case 5 -> 200; case 4 -> 500; case 3 -> 1_000; default -> 2_000;
			};
			return java.util.concurrent.CompletableFuture.supplyAsync(() -> null,
					java.util.concurrent.CompletableFuture.delayedExecutor(delay,
							TimeUnit.MILLISECONDS)).thenCompose(ignored ->
							requestSignatureHelp(params, remainingAttempts - 1));
		});
	}
	private java.util.concurrent.CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<
			List<? extends org.eclipse.lsp4j.Location>, List<? extends org.eclipse.lsp4j.LocationLink>>>
			requestDefinitions(DefinitionParams params) {
		return server.getTextDocumentService().definition(params)
				.orTimeout(4, TimeUnit.SECONDS);
	}
	private static int completionStart(String text, int offset) {
		int start = Math.min(offset, text.length());
		while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
		return start;
	}

	private final class Client implements GLJdtLanguageClient {
		@Override public void publishDiagnostics(PublishDiagnosticsParams params) {
			URI uri;
			try { uri = URI.create(params.getUri()); }
			catch (IllegalArgumentException ignored) { return; }
			OpenDocument state = documents.get(uri);
			if (state == null) return;
			int version = params.getVersion() == null ? state.document().version() : params.getVersion();
			List<GLCodeDiagnostic> converted = new ArrayList<>();
			for (Diagnostic diagnostic : params.getDiagnostics()) {
				int start = offset(state.document().text(), diagnostic.getRange().getStart().getLine(),
						diagnostic.getRange().getStart().getCharacter());
				int end = offset(state.document().text(), diagnostic.getRange().getEnd().getLine(),
						diagnostic.getRange().getEnd().getCharacter());
				if (end <= start) end = Math.min(state.document().text().length(), start + 1);
				if (end <= start) continue;
				String message = diagnostic.getMessage().isLeft()
						? diagnostic.getMessage().getLeft()
						: diagnostic.getMessage().getRight().getValue();
				converted.add(new GLCodeDiagnostic(start, end,
						diagnostic.getSeverity() == DiagnosticSeverity.Error
								? GLCodeDiagnostic.Severity.ERROR : GLCodeDiagnostic.Severity.WARNING,
						message));
			}
			state.listener().accept(new Result(version, converted));
			latestDiagnostics.put(uri, List.copyOf(converted));
		}
		@Override public void telemetryEvent(Object object) { }
		@Override public void showMessage(MessageParams messageParams) { }
		@Override public java.util.concurrent.CompletableFuture<MessageActionItem> showMessageRequest(
				ShowMessageRequestParams requestParams) {
			return java.util.concurrent.CompletableFuture.completedFuture(null);
		}
		@Override public void logMessage(MessageParams message) { }
		@Override public void languageStatus(Object status) { }
		@Override public void languageEventNotification(Object event) { }
		@Override public java.util.concurrent.CompletableFuture<Object> executeClientCommand(
				ExecuteCommandParams params) {
			if (!"java.action.organizeImports.chooseImports".equals(params.getCommand()))
				return java.util.concurrent.CompletableFuture.completedFuture(null);
			return java.util.concurrent.CompletableFuture.completedFuture(
					chooseImportCandidates(params.getArguments()));
		}
	}

	static Object chooseImportCandidates(List<Object> arguments) {
		if (arguments == null || arguments.size() < 2) return List.of();
		com.google.gson.JsonElement selections = new com.google.gson.Gson()
				.toJsonTree(arguments.get(1));
		if (!selections.isJsonArray()) return List.of();
		com.google.gson.JsonArray result = new com.google.gson.JsonArray();
		for (com.google.gson.JsonElement selection : selections.getAsJsonArray()) {
			if (!selection.isJsonObject()) continue;
			com.google.gson.JsonElement candidates = selection.getAsJsonObject().get("candidates");
			if (candidates == null || !candidates.isJsonArray()
					|| candidates.getAsJsonArray().isEmpty()) continue;
			com.google.gson.JsonElement chosen = candidates.getAsJsonArray().get(0);
			for (com.google.gson.JsonElement candidate : candidates.getAsJsonArray()) {
				com.google.gson.JsonElement name = candidate.getAsJsonObject()
						.get("fullyQualifiedName");
				if (name != null && name.getAsString().startsWith("java.util.")) {
					chosen = candidate;
					break;
				}
			}
			result.add(chosen);
		}
		return result;
	}

	private static int offset(String text, int targetLine, int targetCharacter) {
		int offset = 0, line = 0;
		while (offset < text.length() && line < targetLine)
			if (text.charAt(offset++) == '\n') line++;
		return Math.min(text.length(), offset + Math.max(0, targetCharacter));
	}
	static Position position(String text, int targetOffset) {
		int line = 0, lineStart = 0;
		for (int index = 0; index < Math.min(targetOffset, text.length()); index++)
			if (text.charAt(index) == '\n') { line++; lineStart = index + 1; }
		return new Position(line, Math.max(0, targetOffset - lineStart));
	}
	static Optional<GLCodeHover> convertHover(String source, int requestedOffset,
			Hover hover) {
		if (hover == null || hover.getContents() == null) return Optional.empty();
		String contents;
		if (hover.getContents().isRight()) contents = hover.getContents().getRight().getValue();
		else {
			StringBuilder joined = new StringBuilder();
			for (var part : hover.getContents().getLeft()) {
				String value = part.isLeft() ? part.getLeft() : part.getRight().getValue();
				if (value == null || value.isBlank()) continue;
				if (!joined.isEmpty()) joined.append('\n');
				joined.append(value);
			}
			contents = joined.toString();
		}
		contents = plainText(contents);
		if (contents.isBlank()) return Optional.empty();
		int start, end;
		if (hover.getRange() != null) {
			start = offset(source, hover.getRange().getStart().getLine(),
					hover.getRange().getStart().getCharacter());
			end = offset(source, hover.getRange().getEnd().getLine(),
					hover.getRange().getEnd().getCharacter());
		} else {
			start = Math.min(requestedOffset, source.length());
			end = start;
			while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) start--;
			while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
		}
		if (end <= start) {
			start = Math.min(requestedOffset, Math.max(0, source.length() - 1));
			end = Math.min(source.length(), start + 1);
		}
		return end > start ? Optional.of(new GLCodeHover(start, end, contents)) : Optional.empty();
	}
	private static String plainText(String markdown) {
		if (markdown == null) return "";
		return markdown.replace("```java", "").replace("```", "")
				.replaceAll("`([^`]*)`", "$1")
				.replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
				.replace("**", "").replace("__", "")
				.replaceAll("(?m)^\\s*#{1,6}\\s*", "")
				.replaceAll("\\n{3,}", "\n\n").strip();
	}
}
