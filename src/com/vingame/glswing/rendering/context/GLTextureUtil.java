package com.vingame.glswing.rendering.context;

import java.awt.image.BufferedImage;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class GLTextureUtil {

	public static int loadTexture(GL2 gl, BufferedImage image) {
		GLProfile profile = GLProfile.getDefault();
		Texture texture = AWTTextureIO.newTexture(profile, image, true);

		texture.bind(gl);

		texture.setTexParameteri(gl, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
		texture.setTexParameteri(gl, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
		
		return texture.getTextureObject(gl);
	}

}
