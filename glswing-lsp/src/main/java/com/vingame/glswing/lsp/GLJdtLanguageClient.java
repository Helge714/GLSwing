package com.vingame.glswing.lsp;

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.services.LanguageClient;

/** Language client including the informational notifications emitted by JDT LS. */
public interface GLJdtLanguageClient extends LanguageClient {
	@JsonNotification("language/status")
	void languageStatus(Object status);

	@JsonNotification("language/eventNotification")
	void languageEventNotification(Object event);

	@JsonRequest("workspace/executeClientCommand")
	java.util.concurrent.CompletableFuture<Object> executeClientCommand(
			ExecuteCommandParams params);
}
