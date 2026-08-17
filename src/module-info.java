module GLSwingNext {
	requires transitive java.desktop;
	requires java.management;
	requires transitive jogamp.fat;

	exports com.vingame.glswing.component;
	exports com.vingame.glswing.input;
	exports com.vingame.glswing.layout;
	exports com.vingame.glswing.node;
	exports com.vingame.glswing.rendering;
	exports com.vingame.glswing.ui;
}
