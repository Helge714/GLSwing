package com.vingame.glswing.test;

import java.awt.EventQueue;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

import com.vingame.glswing.document.GLDocument;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.ui.GLCodeLocation;
import com.vingame.glswing.ui.GLCodeWorkspace;
import com.vingame.glswing.ui.GLTextDocument;

public final class GLCodeWorkspaceSelfTest {
	private GLCodeWorkspaceSelfTest() { }

	public static void main(String[] args) {
		URI firstUri = URI.create("file:///First.java");
		URI secondUri = URI.create("file:///Second.java");
		GLDocument<GLTextDocument> first = document(firstUri, "First.java",
				"alpha\nbeta alpha\ngamma");
		GLCodeWorkspace workspace = new GLCodeWorkspace();
		workspace.setBounds(0, 0, 800, 520);
		var firstEditor = workspace.openDocument(first);
		check(workspace.tabs().getTabCount() == 1
				&& workspace.getSelectedDocument() == first,
				"Workspace did not open and select its first document");
		check(workspace.tabs().getContentFooter() == workspace.statusBar()
				&& workspace.statusBar().getEditor() == firstEditor,
				"Workspace status bar was not bound to the active editor");
		check(firstEditor.getY() + firstEditor.getHeight()
				<= workspace.statusBar().getY() + 0.01f
				&& workspace.statusBar().getHeight() > 0,
				"Editor content overlaps the workspace status bar");
		firstEditor.setCaretPosition(firstEditor.getDocument().getLineStart(1) + 2);
		check(workspace.statusBar().getLine() == 2
				&& workspace.statusBar().getColumn() == 3,
				"Workspace status bar did not follow the caret");

		firstEditor.showSearchDialog(false);
		check(workspace.isSearchVisible(),
				"Editor Find action did not open the integrated search bar");
		check(workspace.tabs().getContentHeader() != null
				&& workspace.tabs().getContentHeader().isVisible()
				&& firstEditor.getY() > 0,
				"Integrated search was not docked above the selected editor");
		workspace.setSearchText("alpha");
		workspace.findNext();
		check(firstEditor.getSearchMatchCount() == 2,
				"Integrated search did not update the active editor");
		float compactEditorY = firstEditor.getY();
		workspace.showReplace();
		check(firstEditor.getY() > compactEditorY,
				"Replace mode did not expand a second docked row");
		float expandedEditorY = firstEditor.getY();
		workspace.closeSearch();
		workspace.showFind();
		check(firstEditor.getY() == expandedEditorY,
				"Reopened Find did not preserve the previous expansion state");
		workspace.setReplacementText("delta");
		workspace.replaceCurrent();
		check(firstEditor.getText().contains("delta"),
				"Integrated Replace did not update the active editor");
		check(first.isModified() && workspace.tabs().getTitleAt(0).startsWith("* "),
				"Workspace document binding did not publish the dirty tab title");

		workspace.setDocumentResolver(location -> CompletableFuture.completedFuture(
				document(secondUri, "Second.java", "zero\none\ntwo")));
		var secondEditor = workspace.openLocation(
				new GLCodeLocation(secondUri, 2, 1)).toCompletableFuture().join();
		drainEvents();
		check(workspace.tabs().getTabCount() == 2
				&& workspace.getSelectedEditor() == secondEditor
				&& secondEditor.getCaretPosition()
						== secondEditor.getDocument().getLineStart(2) + 1,
				"Resolved navigation did not open the requested source position");

		int secondTab = workspace.tabs().indexOfComponent(secondEditor);
		workspace.tabs().removeTabAt(secondTab);
		workspace.openLocation(new GLCodeLocation(secondUri, 1, 0))
				.toCompletableFuture().join();
		check(workspace.tabs().indexOfComponent(secondEditor) >= 0,
				"Navigation did not restore a retained closed source tab");
		workspace.close();
		System.out.println("GL code workspace self-test passed.");
	}

	private static GLDocument<GLTextDocument> document(URI uri, String title,
			String text) {
		return new GLDocument<>(new GLTextDocument(text), title, uri,
				new GLHistoryManager());
	}

	private static void drainEvents() {
		try { EventQueue.invokeAndWait(() -> { }); }
		catch (Exception failure) { throw new AssertionError(failure); }
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
