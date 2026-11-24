package com.vingame.glswing.ui;

/** Presents Find or Find/Replace UI for a multiline text control. */
@FunctionalInterface
public interface GLTextSearchPresenter {
	void showSearch(GLTextArea target, boolean replaceVisible);
}
