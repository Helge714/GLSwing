package com.vingame.glswing.rendering.context;

import java.awt.image.BufferedImage;

public class GLTexture {
	public final int width;
	public final int height;
	public final float[] uv;
	public final BufferedImage img;

	public GLTexture(BufferedImage img, int width, int height, float[] uv) {
		this.width = width;
		this.height = height;
		this.uv = uv;
		this.img = img;
	}

	public static GLTexture create(BufferedImage img, int uvX, int uxY, int uvWidth, int uvHeight) {
		float width = img.getWidth();
		float height = img.getHeight();
		float locX = uvX / width;
		float locY = uxY / height;
		float[] uv = { //
				locX, locY, //
				locX + uvWidth / width, locY, //
				locX + uvWidth / width, locY + uvHeight / height, //
				locX, locY + uvHeight / height//
		};
		return new GLTexture(img, uvWidth, uvHeight, uv);
	}

	public static GLTexture create(BufferedImage img, int padding) {
		int width = img.getWidth();
		int height = img.getHeight();
		float[] uv = { //
				0, 0, //
				1, 0, //
				1, 1, //
				0, 1 //
		};
		return new GLTexture(img, width, height, uv);
	}
	
}
