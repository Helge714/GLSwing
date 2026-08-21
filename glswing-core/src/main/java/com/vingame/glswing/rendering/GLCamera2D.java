package com.vingame.glswing.rendering;

/** Mutable persistent 2D camera that combines a base projection with a view transform. */
public final class GLCamera2D implements GLView2D {

	private Projection2D projection;
	private float x;
	private float y;
	private float rotation;
	private float zoom = 1;

	public GLCamera2D(Projection2D projection) {
		setProjection(projection);
	}

	public Projection2D getProjection() {
		return projection;
	}

	public void setProjection(Projection2D projection) {
		if (projection == null)
			throw new IllegalArgumentException("Camera projection must not be null");
		this.projection = projection;
	}

	public float getX() {
		return x;
	}

	public float getY() {
		return y;
	}

	public void setPosition(float x, float y) {
		requireFinite(x, y);
		this.x = x;
		this.y = y;
	}

	public void move(float deltaX, float deltaY) {
		setPosition(x + deltaX, y + deltaY);
	}

	public float getRotation() {
		return rotation;
	}

	/** Sets the camera rotation in radians. */
	public void setRotation(float rotation) {
		if (!Float.isFinite(rotation))
			throw new IllegalArgumentException("Camera rotation must be finite");
		this.rotation = rotation;
	}

	public void rotate(float deltaRadians) {
		setRotation(rotation + deltaRadians);
	}

	public float getZoom() {
		return zoom;
	}

	public void setZoom(float zoom) {
		if (!(zoom > 0) || !Float.isFinite(zoom))
			throw new IllegalArgumentException("Camera zoom must be finite and positive");
		this.zoom = zoom;
	}

	public void zoomBy(float factor) {
		if (!(factor > 0) || !Float.isFinite(factor))
			throw new IllegalArgumentException("Camera zoom factor must be finite and positive");
		setZoom(zoom * factor);
	}

	/** Creates only the inverse camera-pose transform, without its projection. */
	public GLTransform createViewTransform() {
		return new GLTransform()
				.scale(zoom)
				.rotate(-rotation)
				.translate(-x, -y);
	}

	@Override
	public GLTransform createViewProjection(float width, float height) {
		return projection.create(width, height).concatenate(createViewTransform());
	}

	private static void requireFinite(float... values) {
		for (float value : values) {
			if (!Float.isFinite(value))
				throw new IllegalArgumentException("Camera position must be finite");
		}
	}
}
