package com.vingame.glswing.input;

/** Standard semantic actions and behavior keys understood by GLSwing controls. */
public final class GLInputActions {

	public static final String HISTORY_UNDO = "history.undo";
	public static final String HISTORY_REDO = "history.redo";
	public static final String FOCUS_NEXT = "focus.next";
	public static final String FOCUS_PREVIOUS = "focus.previous";
	public static final String MENU_MNEMONIC = "menu.mnemonic";
	public static final String DOCUMENT_NEW = "document.new";
	public static final String DOCUMENT_OPEN = "document.open";
	public static final String DOCUMENT_SAVE = "document.save";
	public static final String DOCUMENT_SAVE_AS = "document.save-as";
	public static final String DOCUMENT_SAVE_ALL = "document.save-all";
	public static final String DOCUMENT_CLOSE = "document.close";
	public static final String RENDER_INTERACTION_SETTLE_MILLIS =
			"render.interaction-settle-millis";
	public static final String NODE_INTERACTIVE_CURVE_TOLERANCE_FACTOR =
			"node.interactive-curve-tolerance-factor";

	public static final String ACTIVATE = "control.activate";
	public static final String CANCEL = "control.cancel";
	public static final String NAVIGATE_UP = "navigation.up";
	public static final String NAVIGATE_DOWN = "navigation.down";
	public static final String NAVIGATE_LEFT = "navigation.left";
	public static final String NAVIGATE_RIGHT = "navigation.right";
	public static final String NAVIGATE_HOME = "navigation.home";
	public static final String NAVIGATE_END = "navigation.end";
	public static final String NAVIGATE_PAGE_UP = "navigation.page-up";
	public static final String NAVIGATE_PAGE_DOWN = "navigation.page-down";
	public static final String LIST_EXTEND_SELECTION = "list.extend-selection";
	public static final String LIST_TOGGLE_SELECTION = "list.toggle-selection";
	public static final String LIST_SELECT_ALL = "list.select-all";
	public static final String TREE_EDIT = "tree.edit";

	public static final String SLIDER_PRECISE = "slider.precise";
	public static final String SLIDER_COARSE = "slider.coarse";
	public static final String SLIDER_PRECISE_FACTOR = "slider.precise-factor";
	public static final String SLIDER_COARSE_FACTOR = "slider.coarse-factor";
	public static final String SCROLL_HORIZONTAL = "scroll.horizontal";

	public static final String TEXT_EXTEND_SELECTION = "text.extend-selection";
	public static final String TEXT_COMMAND = "text.command";
	public static final String TEXT_SELECT_ALL = "text.select-all";
	public static final String TEXT_COPY = "text.copy";
	public static final String TEXT_CUT = "text.cut";
	public static final String TEXT_PASTE = "text.paste";
	public static final String TEXT_MOVE_LEFT = "text.move-left";
	public static final String TEXT_MOVE_RIGHT = "text.move-right";
	public static final String TEXT_MOVE_HOME = "text.move-home";
	public static final String TEXT_MOVE_END = "text.move-end";
	public static final String TEXT_BACKSPACE = "text.backspace";
	public static final String TEXT_DELETE = "text.delete";
	public static final String TEXT_COMMIT = "text.commit";
	public static final String TEXT_TOGGLE_BOLD = "text.toggle-bold";
	public static final String TEXT_TOGGLE_ITALIC = "text.toggle-italic";
	public static final String TEXT_ALIGN_LEFT = "text.align-left";
	public static final String TEXT_ALIGN_CENTER = "text.align-center";
	public static final String TEXT_ALIGN_RIGHT = "text.align-right";
	public static final String TEXT_ALIGN_JUSTIFIED = "text.align-justified";
	public static final String CODE_FORMAT = "code.format";
	public static final String CODE_COMMENT = "code.comment";
	public static final String CODE_UNCOMMENT = "code.uncomment";
	public static final String CODE_COMPLETE = "code.complete";
	public static final String CODE_ORGANIZE_IMPORTS = "code.organizeImports";
	public static final String CODE_GO_TO_DEFINITION = "code.goToDefinition";
	public static final String TEXT_HISTORY_COALESCE_MILLIS =
			"text.history-coalesce-millis";

	public static final String NODE_DELETE = "node.delete-selection";
	public static final String NODE_TOGGLE_SELECTION = "node.toggle-selection";
	public static final String NODE_HORIZONTAL_WHEEL = "node.horizontal-wheel";
	public static final String NODE_VERTICAL_WHEEL = "node.vertical-wheel";

	private GLInputActions() {
	}
}
