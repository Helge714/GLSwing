package com.vingame.glswing.ui;

import java.awt.event.MouseEvent;

import javax.accessibility.AccessibleState;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLKeyEvent;
import com.vingame.glswing.input.GLKeyTarget;
import com.vingame.glswing.input.GLMouseEvent;

/**
 * Common interaction state for lightweight GLSwing controls. Geometry remains
 * the responsibility of subclasses; this class only translates input into the
 * familiar normal, hovered, pressed and disabled states.
 */
public abstract class GLControl extends GLContainer implements GLKeyTarget, GLToolTipTarget {

    private GLDriverTarget driverTarget;
    private java.util.function.Supplier<GLPopupMenu> driverMenu;
    private boolean driverAssigned;
    private String driverColor="palette.accent";
    public final GLDriverTarget getDriverTarget(){return driverTarget;}
    public final void setDriverTarget(GLDriverTarget target){driverTarget=target;}
    public final void setDriverContextMenu(java.util.function.Supplier<GLPopupMenu> menu){driverMenu=menu;}
    public final void setDriverIndicator(boolean assigned,String themeColor){driverAssigned=assigned;driverColor=java.util.Objects.requireNonNull(themeColor);repaintGL();}
    public final boolean hasDriverIndicator(){return driverAssigned;}
	private boolean enabled = true;
	private boolean focusable = true;
	private boolean hovered;
	private boolean mousePressed;
	private boolean keyboardPressed;
	private boolean focused;
	private String toolTipText;
	private GLControlState state = GLControlState.NORMAL;

	protected GLControl() {
	}

	protected GLControl(boolean focusable) {
		this.focusable = focusable;
	}
	@Override protected void render(com.vingame.glswing.rendering.GLContext2D context) {
		super.render(context);
        if(driverAssigned)context.drawUIRoundRect(1,1,Math.max(0,getWidth()-2),Math.max(0,getHeight()-2),
            getGLTheme().sliderStyle().cornerRadius(),getGLTheme().values().getFloat("driver.borderWidth",1.5f),
            getGLTheme().values().getColor(driverColor,getGLTheme().values().getColor("palette.accent")),
            com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
		if(isFocused() && getConnectedShape()!=null && !insideHighlightedLayoutGroup())
			context.drawUIRoundRect(0,0,getWidth(),getHeight(),getConnectedShape(),1.5f,
					getGLTheme().values().getColor("palette.accent"),
					com.vingame.glswing.rendering.GLStroke.WidthSpace.SCREEN);
	}
	private boolean insideHighlightedLayoutGroup() {
		for(GLParent parent=getGLParent();parent instanceof GLContainer container;
				parent=container.getGLParent())
			if(container instanceof GLLayoutGroup group && group.isHighlighted())return true;
		return false;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (this.enabled == enabled)
			return;
		this.enabled = enabled;
		fireAccessibleStateChange(AccessibleState.ENABLED, enabled);
		if (!enabled) {
			mousePressed = false;
			keyboardPressed = false;
			clearGLFocus(this);
		}
		updateState();
		controlEnabledChanged(enabled);
	}

	public final boolean isFocusable() {
		return focusable;
	}

	public final void setFocusable(boolean focusable) {
		if (this.focusable == focusable)
			return;
		this.focusable = focusable;
		fireAccessibleStateChange(AccessibleState.FOCUSABLE, focusable);
		if (!focusable)
			clearGLFocus(this);
		focusabilityChanged(focusable);
		repaintGL();
	}

	public final boolean isFocused() {
		return focused;
	}

	public final GLControlState getControlState() {
		return state;
	}

	public final String getToolTipText() { return toolTipText; }
	public final void setToolTipText(String text) {
		toolTipText = text == null || text.isBlank() ? null : text;
	}
	/** Returns the tooltip at the current pointer position, or {@code null}. */
	protected String toolTipTextAt(GLMouseEvent event) {
        String name=getName();
        if(toolTipText==null)return name;
        return toolTipText.equals(name)||toolTipText.startsWith(name+"\n")?toolTipText:name+"\n"+toolTipText;
    }
	/** Optional tooltip text color; {@code null} uses the active theme. */
	protected java.awt.Color toolTipTextColorAt(GLMouseEvent event) { return null; }

	@Override
	protected void detached(GLParent formerParent) {
		GLToolTipManager.detached(this);
	}

	@Override
	public final boolean isFocusTraversable() {
		return enabled && focusable && isVisible();
	}

	@Override
	public final void mouseEntered(GLMouseEvent event) {
		hovered = true;
		updateState();
		controlMouseEntered(event);
		GLToolTipManager.hover(this, event, toolTipTextAt(event),
				toolTipTextColorAt(event));
	}

	@Override
	public final void mouseExited(GLMouseEvent event) {
		hovered = false;
		updateState();
		controlMouseExited(event);
		GLToolTipManager.leave(this);
	}

	@Override
	public final void mouseMoved(GLMouseEvent event) {
		controlMouseMoved(event);
		GLToolTipManager.hover(this, event, toolTipTextAt(event),
				toolTipTextColorAt(event));
	}

	@Override
	public final void mousePressed(GLMouseEvent event) {
		if (enabled && event.getButton() == MouseEvent.BUTTON1) {
			mousePressed = true;
			if (isFocusTraversable())
				requestGLFocus(this);
			updateState();
		}
		controlMousePressed(event);
	}

	@Override
	public final void mouseDragged(GLMouseEvent event) {
		if (mousePressed && event.hasLocalPosition()) {
			hovered = event.getLocalX() >= 0 && event.getLocalY() >= 0
					&& event.getLocalX() <= getWidth()
					&& event.getLocalY() <= getHeight();
			updateState();
		}
		controlMouseDragged(event);
	}

	@Override
	public final void mouseReleased(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1) {
			mousePressed = false;
			updateState();
		}
		controlMouseReleased(event);
	}

	@Override
	public final void mouseCaptureLost(GLMouseEvent event) {
		mousePressed = false;
		hovered = false;
		updateState();
		controlMouseCaptureLost(event);
	}

	@Override
	public final void mouseClicked(GLMouseEvent event) {
        if(event.getButton()==MouseEvent.BUTTON3&&driverMenu!=null){
            var menu=driverMenu.get();if(menu!=null){GLParent host=getGLParent();while(host instanceof GLContainer parent&&parent.getGLParent()!=null)host=parent.getGLParent();if(host!=null){menu.show(host,event.getScreenX(),event.getScreenY());event.consume();return;}}
        }
		controlMouseClicked(event);
	}

	@Override
	public final void focusGained() {
		if (focused)
			return;
		focused = true;
		fireAccessibleStateChange(AccessibleState.FOCUSED, true);
		controlFocusChanged(true);
		repaintGL();
	}

	@Override
	public final void focusLost() {
		if (!focused)
			return;
		focused = false;
		fireAccessibleStateChange(AccessibleState.FOCUSED, false);
		keyboardPressed = false;
		updateState();
		controlFocusChanged(false);
		repaintGL();
	}

	@Override
	public final void keyPressed(GLKeyEvent event) {
		if (enabled)
			controlKeyPressed(event);
	}

	@Override
	public final void keyReleased(GLKeyEvent event) {
		if (enabled)
			controlKeyReleased(event);
	}

	@Override
	public final void keyTyped(GLKeyEvent event) {
		if (enabled)
			controlKeyTyped(event);
	}

	protected final boolean isKeyboardPressed() {
		return keyboardPressed;
	}

	protected final void setKeyboardPressed(boolean pressed) {
		if (keyboardPressed == pressed)
			return;
		keyboardPressed = pressed;
		updateState();
	}

	/** Clears pointer and keyboard gesture state when an overlay is detached. */
	final void resetTransientInteractionState() {
		if (!hovered && !mousePressed && !keyboardPressed)
			return;
		hovered = false;
		mousePressed = false;
		keyboardPressed = false;
		updateState();
	}

	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
	}

	protected void controlFocusChanged(boolean focused) {
	}

	/** Called after this control's enabled state changes. */
	protected void controlEnabledChanged(boolean enabled) {
	}

	/** Called when focus-ring geometry may have become necessary or unnecessary. */
	protected void focusabilityChanged(boolean focusable) {
	}

	protected void controlMouseEntered(GLMouseEvent event) {
	}

	protected void controlMouseExited(GLMouseEvent event) {
	}

	protected void controlMouseMoved(GLMouseEvent event) {
	}

	protected void controlMousePressed(GLMouseEvent event) {
	}

	protected void controlMouseDragged(GLMouseEvent event) {
	}

	protected void controlMouseReleased(GLMouseEvent event) {
	}

	protected void controlMouseCaptureLost(GLMouseEvent event) {
	}

	protected void controlMouseClicked(GLMouseEvent event) {
	}

	protected void controlKeyPressed(GLKeyEvent event) {
	}

	protected void controlKeyReleased(GLKeyEvent event) {
	}

	protected void controlKeyTyped(GLKeyEvent event) {
	}

	private void updateState() {
		GLControlState next = !enabled ? GLControlState.DISABLED
				: keyboardPressed || mousePressed && hovered
						? GLControlState.PRESSED
						: hovered ? GLControlState.HOVERED : GLControlState.NORMAL;
		if (state == next)
			return;
		GLControlState previous = state;
		state = next;
		controlStateChanged(previous, next);
		repaintGL();
	}
}
