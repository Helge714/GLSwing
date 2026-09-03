package com.vingame.glswing.test;

import java.util.concurrent.atomic.AtomicInteger;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.ui.GLDefaultTreeModel;
import com.vingame.glswing.ui.GLList;
import com.vingame.glswing.ui.GLListSelectionModel;
import com.vingame.glswing.ui.GLTree;

/** CPU checks for tree projection, expansion, selection and model changes. */
public final class GLTreeSelfTest {
	private GLTreeSelfTest() { }
	public static void main(String[] args) {
		GLDefaultTreeModel<String> model = new GLDefaultTreeModel<>();
		model.addRoot("project");
		model.addChild("project", "src");
		model.addChild("project", "README.md");
		model.addChild("src", "Main.java");
		GLTree<String> tree = new GLTree<>(model);
		tree.setBounds(0, 0, 300, 200);
		check(tree.getVisibleRowCount() == 1, "Collapsed tree exposed descendants");
		check(tree.setExpanded("project", true) && tree.getVisibleRowCount() == 3,
				"Expanding the root did not reveal direct children");
		check(tree.setExpanded("src", true) && tree.getVisibleRowCount() == 4,
				"Nested expansion did not update the visible projection");
		tree.setSelectedValue("src");
		model.replaceNode("src", "source");
		check(tree.isExpanded("source") && "source".equals(tree.getSelectedValue())
				&& tree.getVisibleRowCount() == 4,
				"Renaming an expanded branch lost expansion or selection");
		AtomicInteger modelChanges = new AtomicInteger();
		model.addTreeModelListener(change -> modelChanges.incrementAndGet());
		model.setChildren("project", java.util.List.of("README.md", "source"));
		check(modelChanges.get() == 1 && model.getChildCount("project") == 2
				&& "Main.java".equals(model.getChild("source", 0)),
				"Batched child replacement lost a retained subtree or fired repeatedly");
		modelChanges.set(0);
		model.setAllowsChildren(java.util.List.of("README.md"), true);
		check(modelChanges.get() == 1 && !model.isLeaf("README.md"),
				"Batched lazy-branch marking did not produce one coherent update");
		tree.setSelectedValue("Main.java");
		tree.setExpanded("source", false);
		check("source".equals(tree.getSelectedValue()),
				"Collapsing a selected descendant did not select its parent");
		model.replaceNode("README.md", "MANUAL.md");
		tree.setSelectedValue("MANUAL.md");
		check("MANUAL.md".equals(tree.getSelectedValue()),
				"Tree did not update after replacing a model node");
		model.setAllowsChildren("MANUAL.md", true);
		check(tree.setExpanded("MANUAL.md", true),
				"Empty lazy node could not enter its expanded state");
		model.addChild("MANUAL.md", "intro.md");
		check(tree.getVisibleRowCount() == 4,
				"Lazily added child did not appear under an expanded node");
		AtomicInteger cancellations = new AtomicInteger();
		tree.setCellEditor(new GLTree.CellEditor<>() {
			@Override public GLContainer createEditor(GLTree<String> source, String value,
					GLList.EditController controller) {
				return new GLContainer() {
					@Override protected void record(GLContext2D context) { }
				};
			}
			@Override public void layoutEditor(GLTree<String> source, String value,
					GLContainer editor, float x, float width, float height) { }
			@Override public boolean commitEditor(GLTree<String> source, String value,
					GLContainer editor) {
				model.replaceNode(value, "overview.md");
				return true;
			}
			@Override public void cancelEditor(GLTree<String> source, String value,
					GLContainer editor) { cancellations.incrementAndGet(); }
		});
		check(tree.startEditing("intro.md") && tree.commitEditing()
				&& cancellations.get() == 0,
				"Committing a model mutation incorrectly cancelled tree editing");
		tree.setSelectedValue("overview.md");
		tree.getSelectionModel().setSelectionMode(
				GLListSelectionModel.SelectionMode.MULTIPLE_INTERVAL);
		System.out.println("GL tree self-test passed.");
	}
	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
