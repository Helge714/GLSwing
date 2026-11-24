package com.vingame.glswing.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Mutable newline-normalized text model shared by multiline text views. */
public final class GLTextDocument {
	@FunctionalInterface
	public interface Listener {
		void documentChanged(GLTextDocument document, Change change);
	}

	public record Change(int offset, String removedText, String insertedText) { }
	public record StyleRun(int start, int end, GLTextStyle style) { }
	public record Snapshot(String text, List<StyleRun> styles) {
		public Snapshot { styles = List.copyOf(styles); }
	}

	private final StringBuilder text = new StringBuilder();
	private final List<Integer> lineStarts = new ArrayList<>();
	private final List<GLTextStyle> characterStyles = new ArrayList<>();
	private final CopyOnWriteArrayList<Listener> listeners =
			new CopyOnWriteArrayList<>();

	public GLTextDocument() { this(""); }

	public GLTextDocument(String text) {
		this.text.append(normalize(text));
		for (int index = 0; index < this.text.length(); index++)
			characterStyles.add(GLTextStyle.PLAIN);
		rebuildLines();
	}

	public int length() { return text.length(); }
	public int getLineCount() { return lineStarts.size(); }
	public String getText() { return text.toString(); }
	public String getText(int start, int end) {
		checkRange(start, end);
		return text.substring(start, end);
	}
	public int getLineStart(int line) { return lineStarts.get(checkLine(line)); }
	public int getLineEnd(int line) {
		int checked = checkLine(line);
		return checked + 1 < lineStarts.size()
				? lineStarts.get(checked + 1) - 1 : text.length();
	}
	public String getLineText(int line) {
		return getText(getLineStart(line), getLineEnd(line));
	}
	public int getLineOfOffset(int offset) {
		if (offset < 0 || offset > text.length())
			throw new IndexOutOfBoundsException("Text offset: " + offset);
		int low = 0, high = lineStarts.size() - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			if (lineStarts.get(middle) <= offset) low = middle + 1;
			else high = middle - 1;
		}
		return Math.max(0, high);
	}
	public GLTextStyle getStyleAt(int offset) {
		if (offset < 0 || offset > text.length())
			throw new IndexOutOfBoundsException("Text offset: " + offset);
		if (characterStyles.isEmpty()) return GLTextStyle.PLAIN;
		return characterStyles.get(Math.min(offset, characterStyles.size() - 1));
	}
	public List<StyleRun> getStyleRuns(int start, int end) {
		checkRange(start, end);
		if (start == end) return List.of();
		List<StyleRun> result = new ArrayList<>();
		int runStart = start;
		GLTextStyle style = characterStyles.get(start);
		for (int index = start + 1; index <= end; index++) {
			if (index == end || !characterStyles.get(index).equals(style)) {
				result.add(new StyleRun(runStart, index, style));
				if (index < end) { runStart = index; style = characterStyles.get(index); }
			}
		}
		return List.copyOf(result);
	}
	public Snapshot snapshot() { return new Snapshot(getText(), getStyleRuns(0, length())); }
	public void restore(Snapshot snapshot) {
		if (snapshot == null) throw new IllegalArgumentException("Snapshot must not be null");
		String previous = getText();
		text.setLength(0);
		text.append(normalize(snapshot.text()));
		characterStyles.clear();
		for (int index = 0; index < text.length(); index++)
			characterStyles.add(GLTextStyle.PLAIN);
		for (StyleRun run : snapshot.styles()) {
			if (run.start() < 0 || run.end() < run.start() || run.end() > text.length())
				throw new IllegalArgumentException("Snapshot contains an invalid style range");
			for (int index = run.start(); index < run.end(); index++)
				characterStyles.set(index, run.style());
		}
		rebuildLines();
		notifyChanged(new Change(0, previous, getText()));
	}
	public void setStyle(int start, int end, GLTextStyle style) {
		checkRange(start, end);
		if (style == null) throw new IllegalArgumentException("Text style must not be null");
		boolean changed = false;
		for (int index = start; index < end; index++) {
			if (!characterStyles.get(index).equals(style)) {
				characterStyles.set(index, style);
				changed = true;
			}
		}
		if (changed) notifyChanged(new Change(start, "", ""));
	}

	public void setText(String value) { replace(0, length(), value); }
	public void insert(int offset, String value) { replace(offset, offset, value); }
	public void delete(int start, int end) { replace(start, end, ""); }

	public void replace(int start, int end, String value) {
		checkRange(start, end);
		String inserted = normalize(value);
		String removed = text.substring(start, end);
		if (removed.equals(inserted)) return;
		GLTextStyle insertionStyle = start < characterStyles.size()
				? characterStyles.get(start)
				: characterStyles.isEmpty() ? GLTextStyle.PLAIN
						: characterStyles.get(characterStyles.size() - 1);
		text.replace(start, end, inserted);
		characterStyles.subList(start, end).clear();
		for (int index = 0; index < inserted.length(); index++)
			characterStyles.add(start + index, insertionStyle);
		rebuildLines();
		notifyChanged(new Change(start, removed, inserted));
	}

	private void notifyChanged(Change change) {
		for (Listener listener : listeners)
			listener.documentChanged(this, change);
	}

	public void addDocumentListener(Listener listener) {
		if (listener == null) throw new IllegalArgumentException("Listener must not be null");
		listeners.addIfAbsent(listener);
	}
	public void removeDocumentListener(Listener listener) { listeners.remove(listener); }

	private void rebuildLines() {
		lineStarts.clear();
		lineStarts.add(0);
		for (int i = 0; i < text.length(); i++)
			if (text.charAt(i) == '\n') lineStarts.add(i + 1);
	}
	private int checkLine(int line) {
		if (line < 0 || line >= lineStarts.size())
			throw new IndexOutOfBoundsException("Text line: " + line);
		return line;
	}
	private void checkRange(int start, int end) {
		if (start < 0 || end < start || end > text.length())
			throw new IndexOutOfBoundsException("Text range: " + start + ".." + end);
	}
	private static String normalize(String value) {
		if (value == null) throw new IllegalArgumentException("Text must not be null");
		return value.replace("\r\n", "\n").replace('\r', '\n');
	}
}
