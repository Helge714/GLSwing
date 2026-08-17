package com.vingame.glswing.demo;

import java.util.ArrayList;
import java.util.List;

import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLLayouts;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.ui.GLFontSpec;
import com.vingame.glswing.ui.GLSlider;
import com.vingame.glswing.ui.GLSliderStyle;
import com.vingame.glswing.ui.GLTheme;

/** One shared retained scene used by the GLJPanel and direct GLCanvas benchmarks. */
final class SliderBenchmarkScene {

	private static final float GAP = 1;
	private static final float TARGET_SLIDER_ASPECT = 4;

	enum SliderKind {
		FIXED("nur Fixed"),
		FREE("nur Free"),
		MIXED("Fixed und Free");

		private final String label;

		SliderKind(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	enum UpdateLoad {
		STATIC("statisch / Recording-Cache"),
		TEN_PERCENT("10 % pro Tick"),
		ALL("alle pro Tick");

		private final String label;

		UpdateLoad(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final GLParent parent;
	private final List<GLSlider> sliders = new ArrayList<>();
	private volatile UpdateLoad updateLoad = UpdateLoad.STATIC;
	private volatile double updateTimeMillis;
	private int updateCursor;
	private double phase;

	SliderBenchmarkScene(GLParent parent) {
		this.parent = parent;
		parent.setGLLayout(GLLayouts.FREE);
		GLSliderStyle compact = GLSliderStyle.DEFAULT
				.withFont(new GLFontSpec(GLFont.SANS_SERIF, GLFont.PLAIN, 9))
				.withGeometry(3, 16, 64)
				.withPadding(GLInsets.all(3));
		parent.setGLTheme(GLTheme.DEFAULT.withSliderStyle(compact));
	}

	void setScenario(int count, SliderKind kind, UpdateLoad updateLoad,
			float width, float height) {
		parent.removeAllGL();
		sliders.clear();
		this.updateLoad = updateLoad;
		updateCursor = 0;
		phase = 0;
		for (int index = 0; index < count; index++) {
			boolean fixed = kind == SliderKind.FIXED
					|| kind == SliderKind.MIXED && (index & 1) == 0;
			GLSlider slider = fixed
					? GLSlider.fixed("", 0, 100, index % 101)
					: GLSlider.free("", index % 101 - 50);
			slider.setStep(1);
			slider.setDragSensitivity(0.2);
			sliders.add(slider);
			parent.addGL(slider);
		}
		arrange(width, height);
		parent.repaintGL();
	}

	void setUpdateLoad(UpdateLoad updateLoad) {
		this.updateLoad = updateLoad;
		if (updateLoad == UpdateLoad.STATIC)
			updateTimeMillis = 0;
	}

	void advanceValues() {
		UpdateLoad load = updateLoad;
		int size = sliders.size();
		if (load == UpdateLoad.STATIC || size == 0)
			return;
		long start = System.nanoTime();
		int updates = load == UpdateLoad.ALL
				? size : Math.max(1, size / 10);
		phase += 0.045;
		for (int offset = 0; offset < updates; offset++) {
			int index = (updateCursor + offset) % size;
			GLSlider slider = sliders.get(index);
			double wave = Math.sin(phase + index * 0.071);
			slider.setValue(slider.getMode() == GLSlider.Mode.FIXED
					? (wave + 1) * 50 : wave * 100);
		}
		updateCursor = (updateCursor + updates) % size;
		updateTimeMillis = (System.nanoTime() - start) / 1_000_000.0;
	}

	double getUpdateTimeMillis() {
		return updateTimeMillis;
	}

	void arrange(float availableWidth, float availableHeight) {
		int count = sliders.size();
		if (count == 0)
			return;
		availableWidth = Math.max(1, availableWidth);
		availableHeight = Math.max(1, availableHeight);
		int columns = Math.max(1, (int) Math.ceil(Math.sqrt(
				count * availableWidth
						/ (availableHeight * TARGET_SLIDER_ASPECT))));
		int rows = (count + columns - 1) / columns;
		float width = Math.max(2,
				(availableWidth - GAP * (columns + 1)) / columns);
		float height = Math.max(2,
				(availableHeight - GAP * (rows + 1)) / rows);
		for (int index = 0; index < count; index++) {
			int column = index % columns;
			int row = index / columns;
			sliders.get(index).setBounds(
					GAP + column * (width + GAP),
					GAP + row * (height + GAP), width, height);
		}
	}
}
