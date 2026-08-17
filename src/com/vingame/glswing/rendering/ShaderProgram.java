package com.vingame.glswing.rendering;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;

/** Owns one linked OpenGL shader program. All methods require a current GL context. */
public final class ShaderProgram {
	private static final String BUILT_IN_SHADER_ROOT =
			"/com/vingame/glswing/shaders/";

	private int id;
	private final Map<String, Integer> uniformLocations = new HashMap<>();
	private final Map<String, Integer> uniformTypes = new HashMap<>();
	private final Set<String> validatedLayouts = new HashSet<>();
	private boolean uniformTypesLoaded;

	private ShaderProgram(int id) {
		this.id = id;
	}

	public static ShaderProgram fromResources(GL3 gl, String vertexPath, String fragmentPath) {
		String vertexSource = loadResource(vertexPath);
		String fragmentSource = loadResource(fragmentPath);
		return create(gl, vertexSource, fragmentSource, vertexPath, fragmentPath);
	}

	/** Loads a shader pair shipped with GLSwing from its resource directory. */
	public static ShaderProgram fromBuiltInResources(GL3 gl, String vertexName,
			String fragmentName) {
		if (vertexName == null || vertexName.isBlank()
				|| fragmentName == null || fragmentName.isBlank())
			throw new IllegalArgumentException("Built-in shader names must not be blank");
		return fromResources(gl, BUILT_IN_SHADER_ROOT + vertexName,
				BUILT_IN_SHADER_ROOT + fragmentName);
	}

	public static ShaderProgram fromSource(GL3 gl, String vertexSource, String fragmentSource) {
		if (gl == null)
			throw new IllegalArgumentException("GL context must not be null");
		if (vertexSource == null || vertexSource.isBlank())
			throw new IllegalArgumentException("Vertex shader source must not be blank");
		if (fragmentSource == null || fragmentSource.isBlank())
			throw new IllegalArgumentException("Fragment shader source must not be blank");
		return create(gl, vertexSource, fragmentSource, "custom vertex shader", "custom fragment shader");
	}

	/**
	 * Creates a layer shader using GLSwing's built-in full-screen vertex shader.
	 * The fragment shader receives {@code vTexCoord} and must declare the managed
	 * uniforms {@code uTexture} and {@code uOpacity}.
	 */
	public static ShaderProgram fromLayerFragmentSource(GL3 gl, String fragmentSource) {
		if (gl == null)
			throw new IllegalArgumentException("GL context must not be null");
		if (fragmentSource == null || fragmentSource.isBlank())
			throw new IllegalArgumentException("Layer fragment shader source must not be blank");
		return create(gl, loadResource(BUILT_IN_SHADER_ROOT + "layer.vert"), fragmentSource,
				"built-in layer vertex shader", "custom layer fragment shader");
	}

	public void use(GL3 gl) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUseProgram(id);
	}

	public void dispose(GL3 gl) {
		if (id != 0) {
			gl.glDeleteProgram(id);
			id = 0;
			uniformLocations.clear();
			uniformTypes.clear();
			validatedLayouts.clear();
			uniformTypesLoaded = false;
		}
	}

	/** Sets a column-major 4x4 matrix on the currently used program. */
	public void setMatrix4(GL3 gl, String uniformName, float[] matrix) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		if (matrix == null || matrix.length != 16)
			throw new IllegalArgumentException("A mat4 uniform requires exactly 16 values");

		int location = uniformLocation(gl, uniformName);
		gl.glUniformMatrix4fv(location, 1, false, matrix, 0);
	}

	public void setVector4(GL3 gl, String uniformName, float x, float y, float z, float w) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUniform4f(uniformLocation(gl, uniformName), x, y, z, w);
	}

	public void setFloat(GL3 gl, String uniformName, float value) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUniform1f(uniformLocation(gl, uniformName), value);
	}

	public void setVector2(GL3 gl, String uniformName, float x, float y) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUniform2f(uniformLocation(gl, uniformName), x, y);
	}

	public void setVector3(GL3 gl, String uniformName, float x, float y, float z) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUniform3f(uniformLocation(gl, uniformName), x, y, z);
	}

	public void setInteger(GL3 gl, String uniformName, int value) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		gl.glUniform1i(uniformLocation(gl, uniformName), value);
	}

	public boolean isDisposed() {
		return id == 0;
	}

	/** Returns whether this program has an active uniform with the given name. */
	public boolean hasUniform(GL3 gl, String uniformName) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		if (uniformName == null || uniformName.isBlank())
			throw new IllegalArgumentException("Uniform name must not be blank");
		return gl.glGetUniformLocation(id, uniformName) >= 0;
	}

	void validateForFilledBatch(GL3 gl) {
		validateLayout(gl, "Filled", new AttributeRequirement[] {
				new AttributeRequirement("aPosition", 0),
				new AttributeRequirement("aColor", 1)
		}, new String[] { "uProjection" });
	}

	void validateForTextureBatch(GL3 gl) {
		validateLayout(gl, "Texture", new AttributeRequirement[] {
				new AttributeRequirement("aPosition", 0),
				new AttributeRequirement("aTexCoord", 1),
				new AttributeRequirement("aColor", 2)
		}, new String[] { "uProjection", "uTexture" });
	}

	void validateForTextBatch(GL3 gl) {
		validateLayout(gl, "Text", new AttributeRequirement[] {
				new AttributeRequirement("aPosition", 0),
				new AttributeRequirement("aTexCoord", 1),
				new AttributeRequirement("aColor", 2)
		}, new String[] { "uProjection", "uTexture", "uMsdfPixelRange" });
	}

	void validateForLayerComposite(GL3 gl) {
		validateLayout(gl, "Layer composite", new AttributeRequirement[0],
				new String[] { "uTexture", "uOpacity" });
	}

	void validateUserUniform(GL3 gl, String uniformName, ShaderUniformValue value) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		if (uniformName == null || uniformName.isBlank())
			throw new IllegalArgumentException("Uniform name must not be blank");
		if (value == null)
			throw new IllegalArgumentException("Uniform value must not be null");

		int actualType = activeUniformType(gl, uniformName);
		if (!value.acceptsGLType(actualType)) {
			throw new IllegalArgumentException("Uniform " + uniformName + " expects "
					+ glTypeName(actualType) + ", but received " + value.typeName());
		}
	}

	private int uniformLocation(GL3 gl, String uniformName) {
		if (uniformName == null || uniformName.isBlank())
			throw new IllegalArgumentException("Uniform name must not be blank");
		int location = uniformLocations.computeIfAbsent(uniformName, name -> gl.glGetUniformLocation(id, name));
		if (location < 0)
			throw new IllegalArgumentException("Uniform not found or inactive: " + uniformName);
		return location;
	}

	private void validateLayout(GL3 gl, String layoutName,
			AttributeRequirement[] attributes, String[] uniforms) {
		if (id == 0)
			throw new IllegalStateException("Shader program has already been disposed");
		if (validatedLayouts.contains(layoutName))
			return;

		for (AttributeRequirement attribute : attributes) {
			int actualLocation = gl.glGetAttribLocation(id, attribute.name);
			if (actualLocation < 0) {
				throw new IllegalArgumentException(layoutName + " shader requires active attribute "
						+ attribute.name + " at location " + attribute.location);
			}
			if (actualLocation != attribute.location) {
				throw new IllegalArgumentException(layoutName + " shader attribute " + attribute.name
						+ " must use location " + attribute.location + ", but uses " + actualLocation);
			}
		}
		for (String uniform : uniforms) {
			if (gl.glGetUniformLocation(id, uniform) < 0)
				throw new IllegalArgumentException(layoutName + " shader requires active uniform " + uniform);
		}
		validatedLayouts.add(layoutName);
	}

	private int activeUniformType(GL3 gl, String uniformName) {
		if (!uniformTypesLoaded)
			loadUniformTypes(gl);
		Integer type = uniformTypes.get(uniformName);
		if (type == null)
			throw new IllegalArgumentException("Shader has no active uniform named " + uniformName);
		return type;
	}

	private void loadUniformTypes(GL3 gl) {
		int[] count = new int[1];
		int[] maximumNameLength = new int[1];
		gl.glGetProgramiv(id, GL2ES2.GL_ACTIVE_UNIFORMS, count, 0);
		gl.glGetProgramiv(id, GL2ES2.GL_ACTIVE_UNIFORM_MAX_LENGTH, maximumNameLength, 0);
		int bufferLength = Math.max(1, maximumNameLength[0]);
		byte[] name = new byte[bufferLength];
		int[] length = new int[1];
		int[] size = new int[1];
		int[] type = new int[1];
		for (int index = 0; index < count[0]; index++) {
			gl.glGetActiveUniform(id, index, bufferLength,
					length, 0, size, 0, type, 0, name, 0);
			String uniformName = new String(name, 0, length[0], StandardCharsets.UTF_8);
			uniformTypes.put(uniformName, type[0]);
		}
		uniformTypesLoaded = true;
	}

	private static String glTypeName(int type) {
		if (type == GL.GL_FLOAT)
			return "float";
		if (type == GL2ES2.GL_FLOAT_VEC2)
			return "vec2";
		if (type == GL2ES2.GL_FLOAT_VEC3)
			return "vec3";
		if (type == GL2ES2.GL_FLOAT_VEC4)
			return "vec4";
		if (type == GL2ES2.GL_INT)
			return "int";
		if (type == GL2ES2.GL_BOOL)
			return "bool";
		if (type == GL2ES2.GL_FLOAT_MAT4)
			return "mat4";
		return "GL type 0x" + Integer.toHexString(type);
	}

	private static final class AttributeRequirement {
		private final String name;
		private final int location;

		AttributeRequirement(String name, int location) {
			this.name = name;
			this.location = location;
		}
	}

	private static ShaderProgram create(GL3 gl, String vertexSource, String fragmentSource,
			String vertexName, String fragmentName) {
		int vertexShader = 0;
		int fragmentShader = 0;
		int program = 0;
		try {
			vertexShader = compile(gl, GL3.GL_VERTEX_SHADER, vertexSource, vertexName);
			fragmentShader = compile(gl, GL3.GL_FRAGMENT_SHADER, fragmentSource, fragmentName);
			program = gl.glCreateProgram();
			gl.glAttachShader(program, vertexShader);
			gl.glAttachShader(program, fragmentShader);
			gl.glLinkProgram(program);

			int[] linked = new int[1];
			gl.glGetProgramiv(program, GL3.GL_LINK_STATUS, linked, 0);
			if (linked[0] == GL.GL_FALSE)
				throw new IllegalStateException("Shader program link failed:\n" + programLog(gl, program));

			return new ShaderProgram(program);
		} catch (RuntimeException error) {
			if (program != 0)
				gl.glDeleteProgram(program);
			throw error;
		} finally {
			if (vertexShader != 0)
				gl.glDeleteShader(vertexShader);
			if (fragmentShader != 0)
				gl.glDeleteShader(fragmentShader);
		}
	}

	private static int compile(GL3 gl, int type, String source, String resourceName) {
		int shader = gl.glCreateShader(type);
		gl.glShaderSource(shader, 1, new String[] { source }, new int[] { source.length() }, 0);
		gl.glCompileShader(shader);

		int[] compiled = new int[1];
		gl.glGetShaderiv(shader, GL3.GL_COMPILE_STATUS, compiled, 0);
		if (compiled[0] == GL.GL_FALSE) {
			String log = shaderLog(gl, shader);
			gl.glDeleteShader(shader);
			throw new IllegalStateException("Shader compilation failed for " + resourceName + ":\n" + log);
		}
		return shader;
	}

	private static String loadResource(String path) {
		try (InputStream input = ShaderProgram.class.getResourceAsStream(path)) {
			if (input == null)
				throw new IllegalArgumentException("Shader resource not found: " + path);
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException error) {
			throw new UncheckedIOException("Could not read shader resource: " + path, error);
		}
	}

	private static String shaderLog(GL3 gl, int shader) {
		int[] length = new int[1];
		gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH, length, 0);
		byte[] bytes = new byte[Math.max(1, length[0])];
		gl.glGetShaderInfoLog(shader, bytes.length, null, 0, bytes, 0);
		return new String(bytes, StandardCharsets.UTF_8).trim();
	}

	private static String programLog(GL3 gl, int program) {
		int[] length = new int[1];
		gl.glGetProgramiv(program, GL3.GL_INFO_LOG_LENGTH, length, 0);
		byte[] bytes = new byte[Math.max(1, length[0])];
		gl.glGetProgramInfoLog(program, bytes.length, null, 0, bytes, 0);
		return new String(bytes, StandardCharsets.UTF_8).trim();
	}
}
