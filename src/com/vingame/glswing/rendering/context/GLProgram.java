package com.vingame.glswing.rendering.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GLProgram {
	public final String vert;
	public final String frag;

	private GLProgram(String vert, String frag) {
		this.vert = vert;
		this.frag = frag;
	}

	public static GLProgram create(String vert, String frag) {
		String vert_;
		String frag_;
		try {
			vert_ = Files.readString(Path.of(vert));
			frag_ = Files.readString(Path.of(frag));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		return new GLProgram(vert_, frag_);
	}

	public static GLProgram createDefault(String vert, String frag) {
		return new GLProgram(load(vert), load(frag));
	}

	private static String load(String resourcePath) {
		try (InputStream in = GLProgram.class.getResourceAsStream("/shader/" + resourcePath)) {
			if (in == null) {
				throw new IllegalArgumentException("Shader not found: " + resourcePath);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof GLProgram other))
			return false;
		return vert.equalsIgnoreCase(other.vert) && frag.equalsIgnoreCase(other.frag);
	}

}
