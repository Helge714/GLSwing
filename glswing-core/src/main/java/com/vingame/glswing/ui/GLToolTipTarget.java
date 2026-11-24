package com.vingame.glswing.ui;

/** Optional tooltip text, independent of focusability or control behavior. */
public interface GLToolTipTarget {
    String getToolTipText();
    void setToolTipText(String text);
}
