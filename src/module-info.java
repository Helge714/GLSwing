/**
 * 
 */
/**
 * 
 */
module GLSwing {
	requires transitive jogamp.fat;
	requires transitive java.desktop;
	requires transitive MathUtils;
	exports com.vingame.glswing.component;
	exports com.vingame.glswing.rendering;
	exports com.vingame.glswing.rendering.batch;
	exports com.vingame.glswing.rendering.context;
}