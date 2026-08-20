package com.vingame.glswing.rendering.batch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.GLAlphaMode;
import com.vingame.glswing.rendering.GLLighting2D;
import com.vingame.glswing.rendering.GLTexture;
import com.vingame.glswing.rendering.ShaderProgram;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;

/** Instanced 2.5D sprite batch with independent color/depth/normal atlas pages. */
public final class SpriteBatch {
	public static final int MAX_TEXTURE_SETS = 5;
	private static final int MAX_INSTANCES = 65_536;
	private static final int INITIAL_INSTANCES = 8_192;
	private static final int FLOATS_PER_INSTANCE = 32;
	private static final float[] IDENTITY_MODEL = {
			1, 0, 0, 0,
			0, 1, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1
	};

	public static final int FLAG_DEPTH_MAP = 1;
	public static final int FLAG_NORMAL_MAP = 2;
	public static final int FLAG_LIT = 4;
	public static final int FLAG_CUTOUT = 8;

	private FloatBuffer instances = Buffers.newDirectFloatBuffer(
			INITIAL_INSTANCES * FLOATS_PER_INSTANCE);
	private IntBuffer pickIds = Buffers.newDirectIntBuffer(INITIAL_INSTANCES);
	private IntBuffer slots = Buffers.newDirectIntBuffer(INITIAL_INSTANCES);
	private IntBuffer flags = Buffers.newDirectIntBuffer(INITIAL_INSTANCES);
	private final TextureSets textures = new TextureSets();
	private final ShaderProgram program;
	private int vao;
	private int instanceVbo;
	private int pickVbo;
	private int slotVbo;
	private int flagVbo;
	private int instanceCount;
	private boolean hasPickableInstances;

	private SpriteBatch(ShaderProgram program, int vao, int instanceVbo,
			int pickVbo, int slotVbo, int flagVbo) {
		this.program = program;
		this.vao = vao;
		this.instanceVbo = instanceVbo;
		this.pickVbo = pickVbo;
		this.slotVbo = slotVbo;
		this.flagVbo = flagVbo;
	}

	public static SpriteBatch create(GL3 gl) {
		ShaderProgram program = ShaderProgram.fromBuiltInResources(
				gl, "sprite.vert", "sprite.frag");
		int[] id = new int[1];
		int vao = 0, instanceVbo = 0, pickVbo = 0, slotVbo = 0, flagVbo = 0;
		try {
			gl.glGenVertexArrays(1, id, 0);
			vao = id[0];
			gl.glBindVertexArray(vao);
			gl.glGenBuffers(1, id, 0);
			instanceVbo = id[0];
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instanceVbo);
			gl.glBufferData(GL.GL_ARRAY_BUFFER,
					(long) INITIAL_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES,
					null, GL.GL_DYNAMIC_DRAW);
			int stride = FLOATS_PER_INSTANCE * Float.BYTES;
			floatAttribute(gl, 0, 2, stride, 0);
			floatAttribute(gl, 1, 2, stride, 2);
			floatAttribute(gl, 2, 2, stride, 4);
			floatAttribute(gl, 3, 2, stride, 6);
			floatAttribute(gl, 4, 4, stride, 8);
			floatAttribute(gl, 5, 4, stride, 12);
			floatAttribute(gl, 6, 4, stride, 16);
			floatAttribute(gl, 7, 4, stride, 20);
			floatAttribute(gl, 8, 4, stride, 24);
			floatAttribute(gl, 9, 2, stride, 28);
			floatAttribute(gl, 10, 2, stride, 30);

			gl.glGenBuffers(1, id, 0);
			pickVbo = id[0];
			integerBuffer(gl, pickVbo, 11, true);
			gl.glGenBuffers(1, id, 0);
			slotVbo = id[0];
			integerBuffer(gl, slotVbo, 12, false);
			gl.glGenBuffers(1, id, 0);
			flagVbo = id[0];
			integerBuffer(gl, flagVbo, 13, false);
			return new SpriteBatch(program, vao, instanceVbo,
					pickVbo, slotVbo, flagVbo);
		} catch (RuntimeException error) {
			deleteBuffer(gl, flagVbo);
			deleteBuffer(gl, slotVbo);
			deleteBuffer(gl, pickVbo);
			deleteBuffer(gl, instanceVbo);
			if (vao != 0)
				gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
			program.dispose(gl);
			throw error;
		} finally {
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
			gl.glBindVertexArray(0);
		}
	}

	public void begin() {
		instances.clear();
		pickIds.clear();
		slots.clear();
		flags.clear();
		textures.clear();
		instanceCount = 0;
		hasPickableInstances = false;
	}

	public boolean accepts(GLTexture color, GLTexture depth, GLTexture normal) {
		return textures.accepts(color, depth, normal);
	}

	public boolean canAdd() {
		return instanceCount < MAX_INSTANCES;
	}

	public void addQuad(GLTexture color, GLTexture depth, GLTexture normal,
			float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float colorU0, float colorV0, float colorU1, float colorV1,
			float depthU0, float depthV0, float depthU1, float depthV1,
			float normalU0, float normalV0, float normalU1, float normalV1,
			float red, float green, float blue, float alpha,
			float baseDepth, float backOffset, float frontOffset,
			float alphaCutoff, int materialFlags, int pickId) {
		if (!accepts(color, depth, normal) || !canAdd())
			throw new IllegalStateException("Sprite batch requires a flush");
		ensureCpuCapacity(instanceCount + 1);
		int slot = textures.slot(color, depth, normal);
		float txDx = x2 - x1;
		float txDy = y2 - y1;
		float txLength = (float) Math.hypot(txDx, txDy);
		float txX = txLength == 0 ? 1 : txDx / txLength;
		float txY = txLength == 0 ? 0 : txDy / txLength;
		float tyDx = x4 - x1;
		float tyDy = y4 - y1;
		float tyLength = (float) Math.hypot(tyDx, tyDy);
		float tyX = tyLength == 0 ? 1 : tyDx / tyLength;
		float tyY = tyLength == 0 ? 0 : tyDy / tyLength;
		putInstance(x1, y1, x2, y2, x3, y3, x4, y4,
				colorU0, colorV0, colorU1, colorV1,
				depthU0, depthV0, depthU1, depthV1,
				normalU0, normalV0, normalU1, normalV1,
				red, green, blue, alpha, baseDepth, backOffset, frontOffset,
				alphaCutoff, txX, txY, tyX, tyY, pickId, slot, materialFlags);
		instanceCount++;
	}

	public int flush(GL3 gl, float[] projection, ShaderProgram override,
			Map<String, ShaderUniformValue> uniforms, GLLighting2D lighting,
			GLAlphaMode alphaMode, PickFramebuffer picking,
			boolean renderColor, boolean renderPicking) {
		if (instanceCount == 0)
			return 0;
		boolean drawPicking = renderPicking && hasPickableInstances;
		if (!renderColor && !drawPicking) {
			reset();
			return 0;
		}
		instances.flip();
		pickIds.flip();
		slots.flip();
		flags.flip();
		textures.bind(gl);
		gl.glBindVertexArray(vao);
		uploadFloat(gl, instanceVbo, instances);
		if (drawPicking)
			uploadInt(gl, pickVbo, pickIds);
		uploadInt(gl, slotVbo, slots);
		uploadInt(gl, flagVbo, flags);
		int drawCalls = 0;
		if (renderColor) {
			configureBlend(gl, alphaMode);
			ShaderProgram active = override == null ? program : override;
			active.use(gl);
			active.setMatrix4(gl, "uProjection", projection);
			active.setMatrix4(gl, "uModel", IDENTITY_MODEL);
			setSamplers(gl, active);
			setLighting(gl, active, lighting);
			for (Map.Entry<String, ShaderUniformValue> uniform : uniforms.entrySet())
				uniform.getValue().apply(gl, active, uniform.getKey());
			gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, instanceCount);
			drawCalls++;
			restoreBlend(gl, alphaMode);
		}
		if (drawPicking) {
			picking.drawSprite(gl, vao, instanceCount, projection, IDENTITY_MODEL,
					textures.colors(), textures.depths(), textures.normals());
			drawCalls++;
		}
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindVertexArray(0);
		textures.unbind(gl);
		gl.glUseProgram(0);
		reset();
		return drawCalls;
	}

	public void dispose(GL3 gl) {
		deleteBuffer(gl, flagVbo); flagVbo = 0;
		deleteBuffer(gl, slotVbo); slotVbo = 0;
		deleteBuffer(gl, pickVbo); pickVbo = 0;
		deleteBuffer(gl, instanceVbo); instanceVbo = 0;
		if (vao != 0) {
			gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
			vao = 0;
		}
		program.dispose(gl);
	}

	/** Internal built-in program shared by renderer-owned resident sprite buffers. */
	public ShaderProgram defaultProgram() {
		return program;
	}

	private void putInstance(float x1, float y1, float x2, float y2,
			float x3, float y3, float x4, float y4,
			float colorU0, float colorV0, float colorU1, float colorV1,
			float depthU0, float depthV0, float depthU1, float depthV1,
			float normalU0, float normalV0, float normalU1, float normalV1,
			float red, float green, float blue, float alpha,
			float baseDepth, float backOffset, float frontOffset,
			float alphaCutoff, float txX, float txY, float tyX, float tyY,
			int pickId, int slot, int materialFlags) {
		instances.put(x1).put(y1).put(x2).put(y2);
		instances.put(x3).put(y3).put(x4).put(y4);
		instances.put(colorU0).put(colorV0).put(colorU1).put(colorV1);
		instances.put(depthU0).put(depthV0).put(depthU1).put(depthV1);
		instances.put(normalU0).put(normalV0).put(normalU1).put(normalV1);
		instances.put(red).put(green).put(blue).put(alpha);
		instances.put(baseDepth).put(backOffset).put(frontOffset).put(alphaCutoff);
		instances.put(txX).put(txY).put(tyX).put(tyY);
		pickIds.put(pickId);
		slots.put(slot);
		flags.put(materialFlags);
		hasPickableInstances |= pickId != 0;
	}

	private void reset() { begin(); }

	private static void floatAttribute(GL3 gl, int location, int count,
			int stride, int floatOffset) {
		gl.glEnableVertexAttribArray(location);
		gl.glVertexAttribPointer(location, count, GL.GL_FLOAT, false, stride,
				(long) floatOffset * Float.BYTES);
		gl.glVertexAttribDivisor(location, 1);
	}

	private static void integerBuffer(GL3 gl, int buffer, int location,
			boolean unsigned) {
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) INITIAL_INSTANCES * Integer.BYTES, null, GL.GL_DYNAMIC_DRAW);
		gl.glEnableVertexAttribArray(location);
		gl.glVertexAttribIPointer(location, 1,
				unsigned ? GL.GL_UNSIGNED_INT : GL2ES2.GL_INT,
				Integer.BYTES, 0);
		gl.glVertexAttribDivisor(location, 1);
	}

	private static void uploadFloat(GL3 gl, int buffer, FloatBuffer values) {
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) values.remaining() * Float.BYTES, values, GL3.GL_STREAM_DRAW);
	}

	private static void uploadInt(GL3 gl, int buffer, IntBuffer values) {
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer);
		gl.glBufferData(GL.GL_ARRAY_BUFFER,
				(long) values.remaining() * Integer.BYTES, values, GL3.GL_STREAM_DRAW);
	}

	private void ensureCpuCapacity(int requiredInstances) {
		int currentInstances = pickIds.capacity();
		if (requiredInstances <= currentInstances)
			return;
		int expandedInstances = Math.min(MAX_INSTANCES,
				Math.max(requiredInstances, currentInstances * 2));
		instances = grow(instances,
				expandedInstances * FLOATS_PER_INSTANCE);
		pickIds = grow(pickIds, expandedInstances);
		slots = grow(slots, expandedInstances);
		flags = grow(flags, expandedInstances);
	}

	private static FloatBuffer grow(FloatBuffer source, int capacity) {
		source.flip();
		FloatBuffer expanded = Buffers.newDirectFloatBuffer(capacity);
		expanded.put(source);
		return expanded;
	}

	private static IntBuffer grow(IntBuffer source, int capacity) {
		source.flip();
		IntBuffer expanded = Buffers.newDirectIntBuffer(capacity);
		expanded.put(source);
		return expanded;
	}

	/** Shared internal state setup for dynamic and resident sprite batches. */
	public static void configureBlend(GL3 gl, GLAlphaMode mode) {
		if (mode == GLAlphaMode.BLEND) {
			gl.glEnable(GL.GL_BLEND);
		} else {
			gl.glDisable(GL.GL_BLEND);
			if (mode == GLAlphaMode.CUTOUT)
				gl.glEnable(GL.GL_SAMPLE_ALPHA_TO_COVERAGE);
		}
	}

	public static void restoreBlend(GL3 gl, GLAlphaMode mode) {
		if (mode == GLAlphaMode.CUTOUT)
			gl.glDisable(GL.GL_SAMPLE_ALPHA_TO_COVERAGE);
		gl.glEnable(GL.GL_BLEND);
	}

	public static void setLighting(GL3 gl, ShaderProgram shader,
			GLLighting2D lighting) {
		GLLighting2D actual = lighting == null ? GLLighting2D.DEFAULT : lighting;
		shader.setVector3(gl, "uAmbientColor",
				actual.ambientColor().getRed() / 255f,
				actual.ambientColor().getGreen() / 255f,
				actual.ambientColor().getBlue() / 255f);
		shader.setFloat(gl, "uAmbientIntensity", actual.ambientIntensity());
		GLLighting2D.DirectionalLight directional = actual.directionalLight();
		if (directional == null) {
			shader.setVector3(gl, "uDirectionalDirection", 0, 0, 1);
			shader.setVector3(gl, "uDirectionalColor", 0, 0, 0);
			shader.setFloat(gl, "uDirectionalIntensity", 0);
		} else {
			shader.setVector3(gl, "uDirectionalDirection",
					directional.x(), directional.y(), directional.z());
			shader.setVector3(gl, "uDirectionalColor",
					directional.color().getRed() / 255f,
					directional.color().getGreen() / 255f,
					directional.color().getBlue() / 255f);
			shader.setFloat(gl, "uDirectionalIntensity", directional.intensity());
		}
	}

	public static void setSamplers(GL3 gl, ShaderProgram shader) {
		for (int slot = 0; slot < MAX_TEXTURE_SETS; slot++) {
			shader.setInteger(gl, "uColorMaps[" + slot + "]", slot);
			shader.setInteger(gl, "uDepthMaps[" + slot + "]",
					MAX_TEXTURE_SETS + slot);
			shader.setInteger(gl, "uNormalMaps[" + slot + "]",
					MAX_TEXTURE_SETS * 2 + slot);
		}
	}

	private static void deleteBuffer(GL3 gl, int buffer) {
		if (buffer != 0)
			gl.glDeleteBuffers(1, new int[] { buffer }, 0);
	}

	private static final class TextureSets {
		private final List<TextureSet> values = new ArrayList<>();
		private final Map<GLTexture, List<TextureSet>> byColor =
				new IdentityHashMap<>();

		boolean accepts(GLTexture color, GLTexture depth, GLTexture normal) {
			if (color == null || color.isDisposed())
				throw new IllegalArgumentException("Sprite color texture must be live");
			return find(color, depth, normal) != null
					|| values.size() < MAX_TEXTURE_SETS;
		}

		int slot(GLTexture color, GLTexture depth, GLTexture normal) {
			TextureSet existing = find(color, depth, normal);
			if (existing != null)
				return existing.slot;
			if (values.size() >= MAX_TEXTURE_SETS)
				throw new IllegalStateException("Sprite texture slots are full");
			TextureSet created = new TextureSet(color,
					depth == null ? color : depth,
					normal == null ? color : normal, values.size());
			values.add(created);
			byColor.computeIfAbsent(color, ignored -> new ArrayList<>()).add(created);
			return created.slot;
		}

		void bind(GL3 gl) {
			for (TextureSet value : values) {
				value.color.bind(gl, value.slot);
				value.depth.bind(gl, MAX_TEXTURE_SETS + value.slot);
				value.normal.bind(gl, MAX_TEXTURE_SETS * 2 + value.slot);
			}
		}

		void unbind(GL3 gl) {
			for (int unit = 0; unit < MAX_TEXTURE_SETS * 3; unit++) {
				gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
				gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
			}
			gl.glActiveTexture(GL.GL_TEXTURE0);
		}

		GLTexture[] colors() { return channel(0); }
		GLTexture[] depths() { return channel(1); }
		GLTexture[] normals() { return channel(2); }

		private GLTexture[] channel(int channel) {
			GLTexture[] result = new GLTexture[values.size()];
			for (TextureSet value : values)
				result[value.slot] = channel == 0 ? value.color
						: channel == 1 ? value.depth : value.normal;
			return result;
		}

		void clear() {
			values.clear();
			byColor.clear();
		}

		private TextureSet find(GLTexture color, GLTexture depth, GLTexture normal) {
			List<TextureSet> candidates = byColor.get(color);
			if (candidates == null)
				return null;
			GLTexture actualDepth = depth == null ? color : depth;
			GLTexture actualNormal = normal == null ? color : normal;
			for (TextureSet candidate : candidates)
				if (candidate.depth == actualDepth && candidate.normal == actualNormal)
					return candidate;
			return null;
		}
	}

	private record TextureSet(GLTexture color, GLTexture depth,
			GLTexture normal, int slot) { }
}
