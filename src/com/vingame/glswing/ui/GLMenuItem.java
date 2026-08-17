package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;

/** One selectable popup-menu row, optionally owning a nested submenu. */
public class GLMenuItem extends GLControl {

	private final CopyOnWriteArrayList<ActionListener> actionListeners =
			new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters =
			new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private final GLRenderParameters secondaryParameters =
			new GLRenderParameters();
	private String text;
	private String acceleratorText = "";
	private String actionCommand;
	private GLPopupMenu ownerMenu;
	private GLPopupMenu subMenu;

	public GLMenuItem(String text) {
		super(false);
		this.text = requireText(text);
		actionCommand = text;
		updatePresentation();
	}

	public final String getText() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		String previous = this.text;
		this.text = text;
		if (actionCommand.equals(previous))
			actionCommand = text;
		updatePresentation();
	}

	public final String getAcceleratorText() {
		return acceleratorText;
	}

	/** Sets display text such as {@code Ctrl+S}; shortcut dispatch remains external. */
	public final void setAcceleratorText(String text) {
		text = requireText(text);
		if (acceleratorText.equals(text))
			return;
		acceleratorText = text;
		updatePresentation();
	}

	public final String getActionCommand() {
		return actionCommand;
	}

	public final void setActionCommand(String actionCommand) {
		this.actionCommand = requireText(actionCommand);
	}

	public final void addActionListener(ActionListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("Action listener must not be null");
		actionListeners.addIfAbsent(listener);
	}

	public final void removeActionListener(ActionListener listener) {
		actionListeners.remove(listener);
	}

	public final ActionListener[] getActionListeners() {
		return actionListeners.toArray(ActionListener[]::new);
	}

	public final GLPopupMenu getSubMenu() {
		return subMenu;
	}

	public final void setSubMenu(GLPopupMenu menu) {
		if (subMenu == menu)
			return;
		if (menu != null)
			menu.claimParentItem(this);
		GLPopupMenu previous = subMenu;
		subMenu = menu;
		if (previous != null)
			previous.releaseParentItem(this);
		updatePresentation();
	}

	public final void doClick() {
		if (!isEnabled())
			return;
		if (subMenu != null) {
			if (ownerMenu != null)
				ownerMenu.openSubMenu(this);
			return;
		}
		activate();
	}

	@Override
	protected void record(GLContext2D context) {
		GLMenuStyle style = menuStyle();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;

		// The visual background is intentionally transparent in NORMAL state.
		// Keep the complete row pickable nonetheless; otherwise only opaque glyph
		// pixels would receive hover events and the selection would flicker.
		context.addUIHitArea(0, 0, width, height);
		context.withParameters(backgroundParameters, () ->
				drawBackground(context, width, height));
		GLFont font = getGLFont(style.font());
		float baseline = (height - font.getLineHeight()) * 0.5f + font.getAscent();
		float left = style.itemHorizontalPadding();
		float right = width - style.itemHorizontalPadding();
		float reservedWidth = 0;
		if (subMenu != null)
			reservedWidth = Math.min(13, height - 8) + style.columnGap();
		else if (!acceleratorText.isEmpty())
			reservedWidth = font.measureText(acceleratorText) + style.columnGap();
		String renderedText = ownerMenu != null && ownerMenu.isEllipsisActive()
				? GLTextFitter.ellipsize(font, text,
						Math.max(0, right - left - reservedWidth))
				: text;
		context.withParameters(textParameters, () ->
				context.drawUIText(font, renderedText, left, baseline, Color.WHITE));

		if (subMenu != null) {
			float arrowSize = Math.min(13, height - 8);
			context.withParameters(secondaryParameters, () ->
					context.drawUIChevron(right - arrowSize,
							(height - arrowSize) * 0.5f, arrowSize, arrowSize,
							true, 1.5f, Color.WHITE, GLStroke.WidthSpace.SCREEN));
		} else if (!acceleratorText.isEmpty()) {
			float acceleratorWidth = font.measureText(acceleratorText);
			context.withParameters(secondaryParameters, () ->
					context.drawUIText(font, acceleratorText,
							right - acceleratorWidth, baseline, Color.WHITE));
		}
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		updateColors();
	}

	@Override
	protected void controlMouseEntered(GLMouseEvent event) {
		if (isEnabled() && ownerMenu != null)
			ownerMenu.itemHovered(this);
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (isEnabled() && event.getButton() == MouseEvent.BUTTON1)
			doClick();
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		if (ownerMenu != null) {
			ownerMenu.mouseWheelMoved(event);
			event.consume();
		}
	}

	@Override
	protected void themeChanged() {
		updatePresentation();
	}

	final void setOwnerMenu(GLPopupMenu menu) {
		if (menu != null && ownerMenu != null && ownerMenu != menu)
			throw new IllegalStateException("Menu item already belongs to another menu");
		ownerMenu = menu;
		updatePresentation();
	}

	final float requiredWidth(GLMenuStyle style) {
		GLFont font = style.font().measurementFont();
		float width = style.itemHorizontalPadding() * 2 + font.measureText(text);
		if (subMenu != null)
			width += style.columnGap();
		else if (!acceleratorText.isEmpty())
			width += style.columnGap() + font.measureText(acceleratorText);
		return width;
	}

	final void menuStyleChanged() {
		updatePresentation();
	}

	private void activate() {
		if (ownerMenu != null)
			ownerMenu.closeSession();
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
				actionCommand, System.currentTimeMillis(), 0);
		for (ActionListener listener : actionListeners)
			listener.actionPerformed(event);
	}

	private void updatePresentation() {
		GLMenuStyle style = menuStyle();
		setIntrinsicPreferredGLSize(new GLSize(
				Math.max(style.minimumWidth(), requiredWidth(style)),
				style.itemHeight()));
		updateColors();
		invalidateRecording();
		if (ownerMenu != null)
			ownerMenu.invalidateMenuLayout();
	}

	private void updateColors() {
		GLMenuStyle style = menuStyle();
		GLControlState state = getControlState();
		Color background = switch (state) {
		case HOVERED -> style.hoverBackground();
		case PRESSED -> style.pressedBackground();
		case NORMAL, DISABLED -> style.hoverBackground();
		};
		backgroundParameters.setColor(background)
				.setOpacity(state == GLControlState.NORMAL
						|| state == GLControlState.DISABLED ? 0 : 1);
		textParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor() : style.textColor());
		secondaryParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor()
				: subMenu == null ? style.acceleratorColor() : style.arrowColor());
		repaintGL();
	}

	private void drawBackground(GLContext2D context, float width, float height) {
		context.fillUIRoundRect(0, 0, width, height,
				Math.min(3, height * 0.25f), Color.WHITE);
	}

	private GLMenuStyle menuStyle() {
		return ownerMenu == null ? GLMenuStyle.DEFAULT : ownerMenu.getGLStyle();
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Menu text must not be null");
		return text;
	}
}
