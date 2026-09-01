package com.vingame.glswing.ui;

import java.awt.Component;
import java.awt.Color;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.Timer;

import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.layout.GLConstraints;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLStroke;

/** Shared delayed tooltip overlay for lightweight controls. */
final class GLToolTipManager {
	private static final Map<GLParent, Session> SESSIONS = new WeakHashMap<>();
	private GLToolTipManager() { }

	static void hover(GLControl owner, GLMouseEvent event, String text, Color color) {
		GLParent host = root(owner);
		if (host == null) return;
		Session session;
		synchronized (SESSIONS) { session = SESSIONS.computeIfAbsent(host, Session::new); }
		session.hover(owner, event.getScreenX(), event.getScreenY(), text, color);
	}
	static void leave(GLControl owner) {
		GLParent host = root(owner);
		if (host == null) return;
		synchronized (SESSIONS) {
			Session session = SESSIONS.get(host);
			if (session != null) session.leave(owner);
		}
	}
	private static GLParent root(GLContainer child) {
		GLParent host = child.getGLParent();
		while (host instanceof GLContainer container && container.getGLParent() != null)
			host = container.getGLParent();
		return host;
	}

	private static final class Session {
		private final GLParent host;
		private final Tip tip = new Tip();
		private final Timer timer;
		private GLControl owner;
		private String text;
		private Color color;
		private float x, y;
		private boolean shown;
		private Session(GLParent host) {
			this.host = host;
			timer = new Timer(500, event -> show());
			timer.setRepeats(false);
		}
		private void hover(GLControl nextOwner, float nextX, float nextY,
				String nextText, Color nextColor) {
			x = nextX; y = nextY;
			if (nextText == null || nextText.isBlank()) { leave(nextOwner); return; }
			boolean changed = owner != nextOwner || !nextText.equals(text)
					|| !java.util.Objects.equals(color, nextColor);
			owner = nextOwner; text = nextText; color = nextColor;
			if (shown) { update(); return; }
			if (changed) timer.restart();
		}
		private void leave(GLControl leaving) {
			if (owner != leaving) return;
			timer.stop(); owner = null; text = null; color = null;
			if (shown) { host.removeGL(tip); shown = false; }
		}
		private void show() {
			if (owner == null || owner.getGLParent() == null || text == null) return;
			if (!shown) { host.addGL(tip, GLConstraints.overlay()); shown = true; }
			update();
		}
		private void update() {
			float hostWidth = host instanceof Component c ? c.getWidth()
					: host instanceof GLContainer c ? c.getWidth() : x + 568;
			tip.setText(text, color, Math.max(120, Math.min(560, hostWidth - 20)));
			GLSize size = tip.getPreferredGLSize();
			float hostHeight = host instanceof Component c ? c.getHeight()
					: host instanceof GLContainer c ? c.getHeight() : y + size.height() + 8;
			tip.setBounds(Math.max(2, Math.min(x + 12, hostWidth - size.width() - 2)),
					Math.max(2, Math.min(y + 18, hostHeight - size.height() - 2)),
					size.width(), size.height());
			tip.bringToFront();
		}
	}

	private static final class Tip extends GLContainer {
		private String text = "";
		private String[] lines = { "" };
		private Color color;
		private float wrapWidth = -1;
		private void setText(String value, Color valueColor, float valueWrapWidth) {
			if (text.equals(value) && java.util.Objects.equals(color, valueColor)
					&& wrapWidth == valueWrapWidth) return;
			text = value; color = valueColor; wrapWidth = valueWrapWidth;
			GLMenuStyle style = getGLTheme().menuStyle();
			GLFont font = getGLFont(style.font());
			lines = wrap(value, font, valueWrapWidth, 16);
			float width = 0;
			for (String line : lines) width = Math.max(width, font.measureText(line));
			setIntrinsicPreferredGLSize(new GLSize(width + 16,
					font.getLineHeight() * lines.length + 10));
			invalidateRecording();
		}
		private static String[] wrap(String value, GLFont font, float maximumWidth,
				int maximumLines) {
			java.util.ArrayList<String> result = new java.util.ArrayList<>();
			String[] paragraphs = value.split("\\R", -1);
			boolean truncated = false;
			outer: for (int paragraphIndex = 0; paragraphIndex < paragraphs.length;
					paragraphIndex++) {
				String paragraph = paragraphs[paragraphIndex];
				String remaining = paragraph;
				if (remaining.isEmpty()) {
					result.add("");
					if (result.size() >= maximumLines && paragraphIndex + 1 < paragraphs.length) {
						truncated = true; break;
					}
					continue;
				}
				while (font.measureText(remaining) > maximumWidth) {
					int best = 0, whitespace = -1, end = 0;
					while (end < remaining.length()) {
						end += Character.charCount(remaining.codePointAt(end));
						if (font.measureText(remaining.substring(0, end)) > maximumWidth) break;
						best = end;
						if (Character.isWhitespace(remaining.codePointBefore(end))) whitespace = end;
					}
					int cut = whitespace > 0 ? whitespace : best > 0 ? best : end;
					result.add(remaining.substring(0, cut).stripTrailing());
					remaining = remaining.substring(cut).stripLeading();
					if (result.size() >= maximumLines) { truncated = true; break outer; }
				}
				if (result.size() >= maximumLines) break;
				result.add(remaining);
				if (result.size() >= maximumLines && paragraphIndex + 1 < paragraphs.length) {
					truncated = true; break;
				}
			}
			if (result.isEmpty()) result.add("");
			if (truncated) {
				int last = maximumLines - 1;
				String finalLine = result.get(last).stripTrailing();
				result.set(last, finalLine + (finalLine.endsWith("…") ? "" : " …"));
				while (result.size() > maximumLines) result.remove(result.size() - 1);
			}
			return result.toArray(String[]::new);
		}
		@Override protected boolean isMouseTargetEnabled() { return false; }
		@Override protected void record(GLContext2D context) {
			GLMenuStyle style = getGLTheme().menuStyle();
			context.fillAndDrawUIRoundRect(0, 0, getWidth(), getHeight(),
					Math.min(4, style.cornerRadius()), Math.max(1, style.borderWidth()),
					style.background(), style.borderColor(), GLStroke.WidthSpace.SCREEN);
			GLFont font = getGLFont(style.font());
			float baseline = 5 + font.getAscent();
			for (String line : lines) {
				context.drawUIText(font, line, 8, baseline,
						color == null ? style.textColor() : color);
				baseline += font.getLineHeight();
			}
		}
	}
}
