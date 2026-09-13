package com.vingame.glswing.ui;

import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLDialog;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.document.GLDocumentFailure;
import com.vingame.glswing.document.GLDocumentFailureHandler;
import com.vingame.glswing.document.GLDocumentMigrationChoice;
import com.vingame.glswing.document.GLDocumentMigrationRequest;
import com.vingame.glswing.document.GLDocumentMigrationRequestHandler;
import com.vingame.glswing.rendering.GLContext2D;

/** Standard localized dialogs for document failures and compatibility upgrades. */
public final class GLDocumentDialogs {
	private GLDocumentDialogs() { }

	/** Creates a lifecycle handler that presents every failure in a themed dialog. */
	public static GLDocumentFailureHandler failureHandler(GLParent owner) {
		if (owner == null) throw new IllegalArgumentException("Dialog owner required");
		return failure -> runOnUi(() -> showFailure(owner, failure));
	}

	/** Creates a lifecycle handler that asks before exposing migrated content. */
	public static GLDocumentMigrationRequestHandler migrationHandler(GLParent owner) {
		if (owner == null) throw new IllegalArgumentException("Dialog owner required");
		return request -> {
			CompletableFuture<GLDocumentMigrationChoice> result = new CompletableFuture<>();
			runOnUi(() -> showMigration(owner, request, result));
			return result;
		};
	}

	public static GLDialog showFailure(GLParent owner, GLDocumentFailure failure) {
		if (owner == null || failure == null)
			throw new IllegalArgumentException("Owner and failure required");
		GLLocalizer text = owner.getGLLocalizer();
		AtomicReference<GLDialog> dialogReference = new AtomicReference<>();
		String description = text.text("glswing.document.error."
				+ failure.kind().name().toLowerCase());
		String cause = failure.cause().getMessage();
		GLContainer content = new GLContainer() {
			private final GLLabel message = label(description);
			private final GLLabel reason = label(cause == null ? "" : cause);
			private final GLLabel path = label(failure.path() == null ? ""
					: failure.path().toString());
			private final GLButton details = new GLButton(text.text(
					"glswing.document.error.details"));
			private final GLButton close = new GLButton(text.text("glswing.dialog.close"));
			private final GLDialogButtonBar actions = new GLDialogButtonBar()
					.addButton(details).addButton(close);
			{
				addGL(message); addGL(reason); addGL(path); addGL(actions);
				details.addActionListener(event -> showDetails(owner, failure));
				close.addActionListener(event -> dialogReference.get().close());
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				message.setBounds(22, 20, Math.max(1, width - 44), 28);
				reason.setBounds(22, 54, Math.max(1, width - 44), 28);
				path.setBounds(22, 88, Math.max(1, width - 44), 28);
				actions.setBounds(22, height - 50, Math.max(1, width - 44), 30);
			}
		};
		GLDialog dialog = new GLDialog(text.text("glswing.document.error.title",
				operation(text, failure)), content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setSize(620, 190); dialog.setResizable(false);
		dialogReference.set(dialog); dialog.show(owner); return dialog;
	}

	private static void showDetails(GLParent owner, GLDocumentFailure failure) {
		GLLocalizer text = owner.getGLLocalizer();
		AtomicReference<GLDialog> reference = new AtomicReference<>();
		GLContainer content = new GLContainer() {
			private final GLTextArea details = new GLTextArea(failure.technicalDetails());
			private final GLButton copy = new GLButton(text.text(
					"glswing.document.error.copy"));
			private final GLButton close = new GLButton(text.text("glswing.dialog.close"));
			private final GLDialogButtonBar actions = new GLDialogButtonBar()
					.addButton(copy).addButton(close);
			{
				details.setEditable(false); details.setLineWrap(false);
				addGL(details); addGL(actions);
				copy.addActionListener(event -> copy(failure.technicalDetails()));
				close.addActionListener(event -> reference.get().close());
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				details.setBounds(18, 18, Math.max(1, width - 36), Math.max(1, height - 72));
				actions.setBounds(18, height - 44, Math.max(1, width - 36), 28);
			}
		};
		GLDialog dialog = new GLDialog(text.text("glswing.document.error.detailsTitle"),
				content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setSize(760, 430); reference.set(dialog); dialog.show(owner);
	}

	private static void showMigration(GLParent owner, GLDocumentMigrationRequest request,
			CompletableFuture<GLDocumentMigrationChoice> result) {
		GLLocalizer text = owner.getGLLocalizer();
		AtomicReference<GLDialog> reference = new AtomicReference<>();
		GLContainer content = new GLContainer() {
			private final GLLabel message = label(text.text(
					"glswing.document.migration.message", request.document().title(),
					request.storedSchemaVersion(), request.currentSchemaVersion()));
			private final GLLabel path = label(request.path().toString());
			private final GLButton upgrade = new GLButton(text.text(
					"glswing.document.migration.upgrade"));
			private final GLButton readOnly = new GLButton(text.text(
					"glswing.document.migration.readOnly"));
			private final GLButton cancel = new GLButton(text.text(
					"glswing.document.migration.cancel"));
			private final GLDialogButtonBar actions = new GLDialogButtonBar()
					.addButton(upgrade).addButton(readOnly).addButton(cancel);
			{
				addGL(message); addGL(path); addGL(actions);
				upgrade.addActionListener(event -> finish(GLDocumentMigrationChoice.UPGRADE));
				readOnly.addActionListener(event -> finish(GLDocumentMigrationChoice.OPEN_READ_ONLY));
				cancel.addActionListener(event -> finish(GLDocumentMigrationChoice.CANCEL));
			}
			private void finish(GLDocumentMigrationChoice choice) {
				if (result.complete(choice)) reference.get().close();
			}
			@Override protected void record(GLContext2D context) { }
			@Override protected void resized(float width, float height) {
				message.setBounds(22, 22, Math.max(1, width - 44), 34);
				path.setBounds(22, 62, Math.max(1, width - 44), 28);
				actions.setBounds(22, height - 50, Math.max(1, width - 44), 30);
			}
		};
		GLDialog dialog = new GLDialog(text.text("glswing.document.migration.title"), content);
		dialog.setDecoration(GLDialog.Decoration.THEMED);
		dialog.setSize(690, 180); dialog.setResizable(false); reference.set(dialog);
		dialog.closedFuture().whenComplete((unused, failure) ->
				result.complete(GLDocumentMigrationChoice.CANCEL));
		try { dialog.show(owner); }
		catch (RuntimeException failure) { result.completeExceptionally(failure); }
	}

	private static GLLabel label(String value) {
		GLLabel label = new GLLabel(value == null ? "" : value);
		label.setVerticalAlignment(GLLabel.VerticalAlignment.CENTER);
		return label;
	}
	private static String operation(GLLocalizer text, GLDocumentFailure failure) {
		return text.text("glswing.document.operation."
				+ failure.operation().name().toLowerCase());
	}
	private static void copy(String value) {
		try { Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(value), null); }
		catch (RuntimeException ignored) { }
	}
	private static void runOnUi(Runnable action) {
		if (EventQueue.isDispatchThread()) action.run();
		else EventQueue.invokeLater(action);
	}
}
