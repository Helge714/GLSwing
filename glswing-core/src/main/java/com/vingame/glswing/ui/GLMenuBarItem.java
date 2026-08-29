package com.vingame.glswing.ui;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;

/** One top-level entry in a {@link GLMenuBar}. */
public final class GLMenuBarItem extends GLControl {

	private final GLPopupMenu menu;
	private final GLRenderParameters backgroundParameters =
			new GLRenderParameters();
	private final GLRenderParameters activeBackgroundParameters =
			new GLRenderParameters();
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private String text;
	private int mnemonic = KeyEvent.VK_UNDEFINED;
	private Integer explicitMnemonic;
	private boolean mnemonicEnabled = true;
	private int displayedMnemonicIndex = -1;
	private GLMenuBar owner;
	private boolean active;

	GLMenuBarItem(String text, GLPopupMenu menu) {
		super(false);
		this.text = requireText(text);
		if (menu == null)
			throw new IllegalArgumentException("Menu-bar popup must not be null");
		this.menu = menu;
		updatePresentation();
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		this.text = text;
		if (owner != null)
			owner.updateMnemonics();
		else
			displayedMnemonicIndex = GLMnemonicSupport.findIndex(text, mnemonic);
		updatePresentation();
	}

	public int getMnemonic() {
		return mnemonic;
	}

	public void setMnemonic(int mnemonic) {
		mnemonic = GLMnemonicSupport.requireMnemonic(mnemonic);
		if (mnemonic == KeyEvent.VK_UNDEFINED) {
			useAutomaticMnemonic();
			return;
		}
		if (owner != null)
			owner.validateExplicitMnemonic(this, mnemonic);
		if (explicitMnemonic != null && explicitMnemonic == mnemonic
				&& mnemonicEnabled)
			return;
		explicitMnemonic = mnemonic;
		mnemonicEnabled = true;
		mnemonicAssignmentChanged();
	}

	public void useAutomaticMnemonic() {
		if (explicitMnemonic == null && mnemonicEnabled)
			return;
		explicitMnemonic = null;
		mnemonicEnabled = true;
		mnemonicAssignmentChanged();
	}

	public boolean isAutomaticMnemonic() {
		return explicitMnemonic == null && mnemonicEnabled;
	}

	public boolean isMnemonicEnabled() {
		return mnemonicEnabled;
	}

	public void setMnemonicEnabled(boolean enabled) {
		if (mnemonicEnabled == enabled)
			return;
		if (enabled && owner != null && explicitMnemonic != null)
			owner.validateExplicitMnemonic(this, explicitMnemonic);
		mnemonicEnabled = enabled;
		mnemonicAssignmentChanged();
	}

	public int getDisplayedMnemonicIndex() {
		return displayedMnemonicIndex;
	}

	public void setDisplayedMnemonicIndex(int index) {
		if (index < -1 || index >= text.length())
			throw new IllegalArgumentException("Mnemonic index is outside the text");
		if (displayedMnemonicIndex == index)
			return;
		displayedMnemonicIndex = index;
		invalidateRecording();
	}

	public GLPopupMenu getMenu() {
		return menu;
	}

	public boolean isActive() {
		return active;
	}

	public void doClick() {
		if (isEnabled() && owner != null)
			owner.itemClicked(this);
	}

	@Override
	protected void record(GLContext2D context) {
		GLMenuBarStyle style = style();
		float width = getWidth();
		float height = getHeight();
		if (width <= 0 || height <= 0)
			return;
		context.addUIHitArea(0, 0, width, height);
		context.withParameters(backgroundParameters, () ->
				context.fillUIRoundRect(0, 1, width, Math.max(0, height - 2),
						style.itemCornerRadius(), Color.WHITE));
		context.withParameters(activeBackgroundParameters, () -> {
			context.fillUIRoundRect(0, 1, width, Math.max(0, height - 1),
					style.itemCornerRadius(), Color.WHITE);
			context.fillUIRoundRect(0, height * 0.5f, width,
					height * 0.5f, 0, Color.WHITE);
		});
		GLFont font = getGLFont(style.font());
		float x = style.itemHorizontalPadding();
		String renderedText = GLTextFitter.ellipsize(font, text,
				Math.max(0, width - style.itemHorizontalPadding() * 2));
		float baseline = (height - font.getLineHeight()) * 0.5f + font.getAscent();
		context.withParameters(textParameters, () ->
				{
					context.drawUIText(font, renderedText, x, baseline, Color.WHITE);
					GLMnemonicSupport.drawUnderline(context, font, renderedText,
							displayedMnemonicIndex, x, baseline, Color.WHITE);
				});
	}

	@Override
	protected void controlStateChanged(GLControlState oldState,
			GLControlState newState) {
		updateColors();
	}

	@Override
	protected void controlMouseEntered(GLMouseEvent event) {
		if (isEnabled() && owner != null)
			owner.itemHovered(this);
	}

	@Override
	protected void controlMouseClicked(GLMouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON1)
			doClick();
	}

	@Override
	protected void themeChanged() {
		updatePresentation();
	}

	void attach(GLMenuBar owner) {
		if (this.owner != null && this.owner != owner)
			throw new IllegalStateException("Menu-bar item already has an owner");
		this.owner = owner;
		updatePresentation();
	}

	void setActive(boolean active) {
		if (this.active == active)
			return;
		this.active = active;
		updateColors();
	}

	void menuBarStyleChanged() {
		updatePresentation();
	}

	boolean matchesMnemonic(int keyCode) {
		return GLMnemonicSupport.matches(mnemonic, keyCode);
	}

	boolean hasExplicitMnemonic() {
		return mnemonicEnabled && explicitMnemonic != null;
	}

	int explicitMnemonic() {
		return explicitMnemonic == null ? KeyEvent.VK_UNDEFINED : explicitMnemonic;
	}

	void applyMnemonic(int mnemonic) {
		if (this.mnemonic == mnemonic)
			return;
		this.mnemonic = mnemonic;
		displayedMnemonicIndex = GLMnemonicSupport.findIndex(text, mnemonic);
		invalidateRecording();
	}

	private void mnemonicAssignmentChanged() {
		if (owner != null)
			owner.updateMnemonics();
		else
			applyMnemonic(!mnemonicEnabled ? KeyEvent.VK_UNDEFINED
					: explicitMnemonic == null ? KeyEvent.VK_UNDEFINED
							: explicitMnemonic);
	}

	private void updatePresentation() {
		GLMenuBarStyle style = style();
		GLFont font = style.font().measurementFont();
		setIntrinsicPreferredGLSize(new GLSize(
				font.measureText(text) + style.itemHorizontalPadding() * 2,
				style.height()));
		updateColors();
		invalidateRecording();
		if (owner != null)
			owner.itemPresentationChanged();
	}

	private void updateColors() {
		GLMenuBarStyle style = style();
		GLControlState state = getControlState();
		Color background = state == GLControlState.PRESSED ? style.pressedBackground()
				: state == GLControlState.HOVERED ? style.hoverBackground()
				: style.hoverBackground();
		backgroundParameters.setColor(background).setOpacity(
				!active && (state == GLControlState.PRESSED
						|| state == GLControlState.HOVERED) ? 1 : 0);
		activeBackgroundParameters.setColor(style.activeBackground())
				.setOpacity(active ? 1 : 0);
		textParameters.setColor(state == GLControlState.DISABLED
				? style.disabledTextColor() : style.textColor());
		repaintGL();
	}

	private GLMenuBarStyle style() {
		return owner == null ? GLMenuBarStyle.DEFAULT : owner.getGLStyle();
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Menu-bar text must not be null");
		return text;
	}
}
