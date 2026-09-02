package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CopyOnWriteArrayList;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLIcon;
import com.vingame.glswing.rendering.GLRenderParameters;
import com.vingame.glswing.rendering.GLStroke;

/** One selectable popup-menu row, optionally owning a nested submenu. */
public class GLMenuItem extends GLControl {
	@Override public boolean preservesKeyboardFocusOnPress() { return true; }

	private final CopyOnWriteArrayList<ActionListener> actionListeners =
			new CopyOnWriteArrayList<>();
	private final GLRenderParameters backgroundParameters =
			new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private final GLRenderParameters secondaryParameters =
			new GLRenderParameters();
	private String text;
	private int mnemonic = KeyEvent.VK_UNDEFINED;
	private Integer explicitMnemonic;
	private boolean mnemonicEnabled = true;
	private int displayedMnemonicIndex = -1;
	private String acceleratorText = "";
	private String actionCommand;
	private GLPopupMenu ownerMenu;
	private GLPopupMenu subMenu;
	private GLIcon icon;
	private GLIcon disabledIcon;
	private boolean keyboardSelected;

	public GLMenuItem(String text) {
		super(false);
		this.text = displayText(text);
		actionCommand = this.text;
		updatePresentation();
	}

	public GLMenuItem(String text, int mnemonic) {
		this(text);
		setMnemonic(mnemonic);
	}

	public GLMenuItem(String text, ActionListener listener) {
		this(text);
		addActionListener(listener);
	}

	public GLMenuItem(String text, int mnemonic, ActionListener listener) {
		this(text, mnemonic);
		addActionListener(listener);
	}

	public final String getText() {
		return text;
	}

	public final void setText(String text) {
		text = displayText(text);
		if (this.text.equals(text))
			return;
		String previous = this.text;
		this.text = text;
		if (ownerMenu != null)
			ownerMenu.updateMnemonics();
		else
			displayedMnemonicIndex = GLMnemonicSupport.findIndex(text, mnemonic);
		if (actionCommand.equals(previous))
			actionCommand = text;
		updatePresentation();
	}

	private static String displayText(String text) {
		return GLDisplayText.normalizeEllipses(requireText(text));
	}

	public final int getMnemonic() {
		return mnemonic;
	}

	/** Pins an explicit key; {@code VK_UNDEFINED} restores automatic allocation. */
	public final void setMnemonic(int mnemonic) {
		mnemonic = GLMnemonicSupport.requireMnemonic(mnemonic);
		if (mnemonic == KeyEvent.VK_UNDEFINED) {
			useAutomaticMnemonic();
			return;
		}
		if (ownerMenu != null)
			ownerMenu.validateExplicitMnemonic(this, mnemonic);
		if (explicitMnemonic != null && explicitMnemonic == mnemonic
				&& mnemonicEnabled)
			return;
		explicitMnemonic = mnemonic;
		mnemonicEnabled = true;
		mnemonicAssignmentChanged();
	}

	/** Restores conflict-free mnemonic selection from the current visible text. */
	public final void useAutomaticMnemonic() {
		if (explicitMnemonic == null && mnemonicEnabled)
			return;
		explicitMnemonic = null;
		mnemonicEnabled = true;
		mnemonicAssignmentChanged();
	}

	public final boolean isAutomaticMnemonic() {
		return explicitMnemonic == null && mnemonicEnabled;
	}

	public final boolean isMnemonicEnabled() {
		return mnemonicEnabled;
	}

	/** Includes or excludes this row from mnemonic allocation and dispatch. */
	public final void setMnemonicEnabled(boolean enabled) {
		if (mnemonicEnabled == enabled)
			return;
		if (enabled && ownerMenu != null && explicitMnemonic != null)
			ownerMenu.validateExplicitMnemonic(this, explicitMnemonic);
		mnemonicEnabled = enabled;
		mnemonicAssignmentChanged();
	}

	public final int getDisplayedMnemonicIndex() {
		return displayedMnemonicIndex;
	}

	public final void setDisplayedMnemonicIndex(int index) {
		if (index < -1 || index >= text.length())
			throw new IllegalArgumentException("Mnemonic index is outside the text");
		if (displayedMnemonicIndex == index)
			return;
		displayedMnemonicIndex = index;
		invalidateRecording();
	}

	public final String getAcceleratorText() {
		return acceleratorText;
	}

	public final GLIcon getIcon() {
		return icon;
	}

	/** Sets the leading icon; {@code null} removes it. */
	public final void setIcon(GLIcon icon) {
		if (this.icon == icon)
			return;
		this.icon = icon;
		updatePresentation();
	}

	public final void clearIcon() {
		setIcon(null);
	}

	public final GLIcon getDisabledIcon() {
		return disabledIcon;
	}

	/** Sets an optional disabled-state icon; the normal icon is the fallback. */
	public final void setDisabledIcon(GLIcon icon) {
		if (disabledIcon == icon)
			return;
		disabledIcon = icon;
		invalidateRecording();
	}

	public final void clearDisabledIcon() {
		setDisabledIcon(null);
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
		if (ownerMenu != null && ownerMenu.hasIconColumn()) {
			float iconSize = ownerMenu.iconSize();
			GLIcon renderedIcon = !isEnabled() && disabledIcon != null
					? disabledIcon : icon;
			if (renderedIcon != null) {
				Color tint = isEnabled() ? style.textColor()
						: style.disabledTextColor();
				context.drawUIIcon(renderedIcon, left,
						(height - iconSize) * 0.5f, iconSize, iconSize, tint);
			}
			left += iconSize + style.columnGap();
		}
		float reservedWidth = 0;
		if (subMenu != null)
			reservedWidth = Math.min(13, height - 8) + style.columnGap();
		else if (!acceleratorText.isEmpty())
			reservedWidth = font.measureText(acceleratorText) + style.columnGap();
		String renderedText = ownerMenu != null && ownerMenu.isEllipsisActive()
				? GLTextFitter.ellipsize(font, text,
						Math.max(0, right - left - reservedWidth))
				: text;
		float textLeft = left;
		var textShadow = getGLTheme().textShadow("menu");
		context.drawUITextShadow(font, renderedText, textLeft, baseline,
				textShadow);
		GLMnemonicSupport.drawUnderlineShadow(context, font, renderedText,
				displayedMnemonicIndex, textLeft, baseline, textShadow);
		context.withParameters(textParameters, () -> {
			context.drawUIText(font, renderedText, textLeft, baseline, Color.WHITE);
			GLMnemonicSupport.drawUnderline(context, font, renderedText,
					displayedMnemonicIndex, textLeft, baseline, Color.WHITE);
		});

		if (subMenu != null) {
			float arrowSize = Math.min(13, height - 8);
			context.withParameters(secondaryParameters, () ->
					context.drawUIChevron(right - arrowSize,
							(height - arrowSize) * 0.5f, arrowSize, arrowSize,
							true, 1.5f, Color.WHITE, GLStroke.WidthSpace.SCREEN));
		} else if (!acceleratorText.isEmpty()) {
			float acceleratorWidth = font.measureText(acceleratorText);
			context.drawUITextShadow(font, acceleratorText,
					right - acceleratorWidth, baseline,
					getGLTheme().textShadow("menu"));
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
	protected void controlMouseExited(GLMouseEvent event) {
		if (isEnabled() && ownerMenu != null)
			ownerMenu.itemExited(this);
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
		if (menu == null)
			applyMnemonic(!mnemonicEnabled ? KeyEvent.VK_UNDEFINED
					: explicitMnemonic == null ? KeyEvent.VK_UNDEFINED
							: explicitMnemonic);
		updatePresentation();
	}

	final float requiredWidth(GLMenuStyle style) {
		GLFont font = style.font().measurementFont();
		float width = style.itemHorizontalPadding() * 2 + font.measureText(text);
		if (ownerMenu != null && ownerMenu.hasIconColumn())
			width += ownerMenu.iconSize() + style.columnGap();
		if (subMenu != null)
			width += style.columnGap();
		else if (!acceleratorText.isEmpty())
			width += style.columnGap() + font.measureText(acceleratorText);
		return width;
	}

	final void menuStyleChanged() {
		updatePresentation();
	}

	final boolean matchesMnemonic(int keyCode) {
		return GLMnemonicSupport.matches(mnemonic, keyCode);
	}

	final boolean hasExplicitMnemonic() {
		return mnemonicEnabled && explicitMnemonic != null;
	}

	final int explicitMnemonic() {
		return explicitMnemonic == null ? KeyEvent.VK_UNDEFINED : explicitMnemonic;
	}

	final void applyMnemonic(int mnemonic) {
		if (this.mnemonic == mnemonic)
			return;
		this.mnemonic = mnemonic;
		displayedMnemonicIndex = GLMnemonicSupport.findIndex(text, mnemonic);
		invalidateRecording();
	}

	private void mnemonicAssignmentChanged() {
		if (ownerMenu != null)
			ownerMenu.updateMnemonics();
		else
			applyMnemonic(!mnemonicEnabled ? KeyEvent.VK_UNDEFINED
					: explicitMnemonic == null ? KeyEvent.VK_UNDEFINED
							: explicitMnemonic);
	}

	private void activate() {
		boolean popupAction = ownerMenu != null && ownerMenu.isShowing();
		if (ownerMenu != null)
			ownerMenu.closeSessionAfterAction();
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
				actionCommand, System.currentTimeMillis(), 0);
		Runnable notify = () -> {
			for (ActionListener listener : actionListeners)
				listener.actionPerformed(event);
		};
		if (popupAction) EventQueue.invokeLater(notify);
		else notify.run();
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
				.setOpacity(!keyboardSelected && (state == GLControlState.NORMAL
						|| state == GLControlState.DISABLED) ? 0 : 1);
		textParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor() : style.textColor());
		secondaryParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor()
				: subMenu == null ? style.acceleratorColor() : style.arrowColor());
		repaintGL();
	}

	final void setKeyboardSelected(boolean selected) {
		if (keyboardSelected == selected) return;
		keyboardSelected = selected;
		updateColors();
	}

	private void drawBackground(GLContext2D context, float width, float height) {
		context.fillUIRoundRect(0, 0, width, height,
				menuStyle().itemCornerRadius(), Color.WHITE);
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
