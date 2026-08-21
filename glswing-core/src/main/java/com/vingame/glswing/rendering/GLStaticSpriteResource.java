package com.vingame.glswing.rendering;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL3;
import com.vingame.glswing.rendering.batch.SpriteBatch;
import com.vingame.glswing.rendering.internal.PickFramebuffer;
import com.vingame.glswing.rendering.internal.ShaderUniformValue;

/** Renderer-local GPU representation of one {@link GLSpriteBuffer}. */
final class GLStaticSpriteResource {
    private static final int FLOATS_PER_INSTANCE = 32;
    private static final int MAX_INSTANCES = 65_536;

    private final List<Section> sections;

    private GLStaticSpriteResource(List<Section> sections) {
        this.sections = sections;
    }

    static GLStaticSpriteResource create(GL3 gl, GLRenderer2D renderer,
            GLSpriteBuffer buffer) {
        List<Section> sections = new ArrayList<>();
        SectionBuilder current = null;
        try {
            for (GLSpriteBuffer.Entry entry : buffer.entries()) {
                GLMaterial2D material = entry.material;
                renderer.validateSpriteMaterial(material);
                GLTextureRegion color = renderer.resolveImage(material.getColorMap());
                GLTextureRegion depth = material.hasDepthMap()
                        ? renderer.resolveImage(material.getDepthMap()) : color;
                GLTextureRegion normal = material.hasNormalMap()
                        ? renderer.resolveImage(material.getNormalMap()) : color;
                if (current == null || !current.accepts(entry, color, depth, normal)) {
                    if (current != null)
                        sections.add(current.upload(gl));
                    current = new SectionBuilder(entry);
                }
                current.add(entry, color, depth, normal);
            }
            if (current != null)
                sections.add(current.upload(gl));
            return new GLStaticSpriteResource(List.copyOf(sections));
        } catch (RuntimeException error) {
            for (Section section : sections)
                section.dispose(gl);
            throw error;
        }
    }

    int draw(GL3 gl, GLRenderer2D renderer, float[] projection, float[] model,
            GLLighting2D lighting, PickFramebuffer picking,
            boolean renderColor, boolean renderPicking) {
        int calls = 0;
        for (Section section : sections)
            calls += section.draw(gl, renderer, projection, model, lighting,
                    picking, renderColor, renderPicking);
        return calls;
    }

    void dispose(GL3 gl) {
        for (Section section : sections)
            section.dispose(gl);
    }

    private static final class SectionBuilder {
        private final ShaderProgram shader;
        private final Map<String, ShaderUniformValue> uniforms;
        private final GLDepthState depthState;
        private final GLAlphaMode alphaMode;
        private final int barrier;
        private final List<TextureSet> textures = new ArrayList<>();
        private float[] values = new float[8_192 * FLOATS_PER_INSTANCE];
        private int[] slots = new int[8_192];
        private int[] flags = new int[8_192];
        private GLSpriteBuffer.Entry[] entries = new GLSpriteBuffer.Entry[8_192];
        private int count;

        SectionBuilder(GLSpriteBuffer.Entry first) {
            GLMaterial2D material = first.material;
            shader = material.getShader();
            uniforms = material.uniformValues();
            depthState = material.getDepthState();
            alphaMode = material.getAlphaMode();
            barrier = first.sectionBarrier;
        }

        boolean accepts(GLSpriteBuffer.Entry entry, GLTextureRegion color,
                GLTextureRegion depth, GLTextureRegion normal) {
            GLMaterial2D material = entry.material;
            if (count >= MAX_INSTANCES || barrier != entry.sectionBarrier
                    || shader != material.getShader()
                    || !uniforms.equals(material.uniformValues())
                    || !depthState.equals(material.getDepthState())
                    || alphaMode != material.getAlphaMode())
                return false;
            return findTexture(color.texture(), depth.texture(), normal.texture()) >= 0
                    || textures.size() < SpriteBatch.MAX_TEXTURE_SETS;
        }

        void add(GLSpriteBuffer.Entry entry, GLTextureRegion color,
                GLTextureRegion depth, GLTextureRegion normal) {
            ensureCapacity(count + 1);
            int slot = textureSlot(color.texture(), depth.texture(), normal.texture());
            GLTransform transform = entry.localTransform;
            float x2 = entry.x + entry.width;
            float y2 = entry.y + entry.height;
            float x00 = transform.transformX(entry.x, entry.y);
            float y00 = transform.transformY(entry.x, entry.y);
            float x10 = transform.transformX(x2, entry.y);
            float y10 = transform.transformY(x2, entry.y);
            float x11 = transform.transformX(x2, y2);
            float y11 = transform.transformY(x2, y2);
            float x01 = transform.transformX(entry.x, y2);
            float y01 = transform.transformY(entry.x, y2);
            float txX = x10 - x00;
            float txY = y10 - y00;
            float txLength = (float) Math.hypot(txX, txY);
            if (txLength == 0) { txX = 1; txY = 0; }
            else { txX /= txLength; txY /= txLength; }
            float tyX = x01 - x00;
            float tyY = y01 - y00;
            float tyLength = (float) Math.hypot(tyX, tyY);
            if (tyLength == 0) { tyX = 0; tyY = 1; }
            else { tyX /= tyLength; tyY /= tyLength; }
            int offset = count * FLOATS_PER_INSTANCE;
            float[] v = values;
            v[offset++] = x00; v[offset++] = y00; v[offset++] = x10; v[offset++] = y10;
            v[offset++] = x11; v[offset++] = y11; v[offset++] = x01; v[offset++] = y01;
            v[offset++] = color.u0(); v[offset++] = color.v0();
            v[offset++] = color.u1(); v[offset++] = color.v1();
            v[offset++] = depth.u0(); v[offset++] = depth.v0();
            v[offset++] = depth.u1(); v[offset++] = depth.v1();
            v[offset++] = normal.u0(); v[offset++] = normal.v0();
            v[offset++] = normal.u1(); v[offset++] = normal.v1();
            v[offset++] = entry.tint.getRed() / 255f;
            v[offset++] = entry.tint.getGreen() / 255f;
            v[offset++] = entry.tint.getBlue() / 255f;
            v[offset++] = entry.tint.getAlpha() / 255f;
            GLMaterial2D material = entry.material;
            v[offset++] = entry.depth;
            v[offset++] = material.getDepthBackOffset();
            v[offset++] = material.getDepthFrontOffset();
            v[offset++] = material.getAlphaCutoff();
            v[offset++] = txX; v[offset++] = txY; v[offset++] = tyX; v[offset] = tyY;
            slots[count] = slot;
            flags[count] = (material.hasDepthMap() ? SpriteBatch.FLAG_DEPTH_MAP : 0)
                    | (material.hasNormalMap() ? SpriteBatch.FLAG_NORMAL_MAP : 0)
                    | (material.isLit() ? SpriteBatch.FLAG_LIT : 0)
                    | (material.getAlphaMode() == GLAlphaMode.CUTOUT
                            ? SpriteBatch.FLAG_CUTOUT : 0);
            entries[count] = entry;
            count++;
        }

        Section upload(GL3 gl) {
            return Section.upload(gl, shader, uniforms, depthState, alphaMode,
                    textures, values, slots, flags, entries, count);
        }

        private int textureSlot(GLTexture color, GLTexture depth, GLTexture normal) {
            int existing = findTexture(color, depth, normal);
            if (existing >= 0)
                return existing;
            if (textures.size() >= SpriteBatch.MAX_TEXTURE_SETS)
                throw new IllegalStateException("Resident sprite texture slots are full");
            textures.add(new TextureSet(color, depth, normal));
            return textures.size() - 1;
        }

        private int findTexture(GLTexture color, GLTexture depth, GLTexture normal) {
            for (int index = 0; index < textures.size(); index++) {
                TextureSet value = textures.get(index);
                if (value.color == color && value.depth == depth && value.normal == normal)
                    return index;
            }
            return -1;
        }

        private void ensureCapacity(int required) {
            if (required <= slots.length)
                return;
            int expanded = Math.min(MAX_INSTANCES,
                    Math.max(required, slots.length * 2));
            values = Arrays.copyOf(values, expanded * FLOATS_PER_INSTANCE);
            slots = Arrays.copyOf(slots, expanded);
            flags = Arrays.copyOf(flags, expanded);
            entries = Arrays.copyOf(entries, expanded);
        }
    }

    private static final class Section {
        private final ShaderProgram shader;
        private final Map<String, ShaderUniformValue> uniforms;
        private final GLDepthState depthState;
        private final GLAlphaMode alphaMode;
        private final GLTexture[] colors;
        private final GLTexture[] depths;
        private final GLTexture[] normals;
        private final GLSpriteBuffer.Entry[] entries;
        private final IntBuffer pickIds;
        private int vao;
        private int instanceVbo;
        private int pickVbo;
        private int slotVbo;
        private int flagVbo;
        private final int count;

        private Section(ShaderProgram shader, Map<String, ShaderUniformValue> uniforms,
                GLDepthState depthState, GLAlphaMode alphaMode,
                GLTexture[] colors, GLTexture[] depths, GLTexture[] normals,
                GLSpriteBuffer.Entry[] entries, int vao, int instanceVbo,
                int pickVbo, int slotVbo, int flagVbo, int count) {
            this.shader = shader;
            this.uniforms = uniforms;
            this.depthState = depthState;
            this.alphaMode = alphaMode;
            this.colors = colors;
            this.depths = depths;
            this.normals = normals;
            this.entries = entries;
            this.vao = vao;
            this.instanceVbo = instanceVbo;
            this.pickVbo = pickVbo;
            this.slotVbo = slotVbo;
            this.flagVbo = flagVbo;
            this.count = count;
            pickIds = Buffers.newDirectIntBuffer(count);
        }

        static Section upload(GL3 gl, ShaderProgram shader,
                Map<String, ShaderUniformValue> uniforms, GLDepthState depthState,
                GLAlphaMode alphaMode, List<TextureSet> textures,
                float[] values, int[] slots, int[] flags,
                GLSpriteBuffer.Entry[] entries, int count) {
            int[] ids = new int[5];
            int vao = 0;
            try {
                gl.glGenVertexArrays(1, ids, 0);
                vao = ids[0];
                gl.glBindVertexArray(vao);
                gl.glGenBuffers(4, ids, 1);
                int instanceVbo = ids[1], pickVbo = ids[2];
                int slotVbo = ids[3], flagVbo = ids[4];
                FloatBuffer instanceData = Buffers.newDirectFloatBuffer(
                        Arrays.copyOf(values, count * FLOATS_PER_INSTANCE));
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instanceVbo);
                gl.glBufferData(GL.GL_ARRAY_BUFFER,
                        (long) count * FLOATS_PER_INSTANCE * Float.BYTES,
                        instanceData, GL.GL_STATIC_DRAW);
                int stride = FLOATS_PER_INSTANCE * Float.BYTES;
                floatAttribute(gl, 0, 2, stride, 0); floatAttribute(gl, 1, 2, stride, 2);
                floatAttribute(gl, 2, 2, stride, 4); floatAttribute(gl, 3, 2, stride, 6);
                floatAttribute(gl, 4, 4, stride, 8); floatAttribute(gl, 5, 4, stride, 12);
                floatAttribute(gl, 6, 4, stride, 16); floatAttribute(gl, 7, 4, stride, 20);
                floatAttribute(gl, 8, 4, stride, 24); floatAttribute(gl, 9, 2, stride, 28);
                floatAttribute(gl, 10, 2, stride, 30);
                integerAttribute(gl, pickVbo, 11, true, null, count, GL3.GL_STREAM_DRAW);
                integerAttribute(gl, slotVbo, 12, false,
                        Buffers.newDirectIntBuffer(Arrays.copyOf(slots, count)), count,
                        GL.GL_STATIC_DRAW);
                integerAttribute(gl, flagVbo, 13, false,
                        Buffers.newDirectIntBuffer(Arrays.copyOf(flags, count)), count,
                        GL.GL_STATIC_DRAW);
                GLTexture[] colors = new GLTexture[textures.size()];
                GLTexture[] depths = new GLTexture[textures.size()];
                GLTexture[] normals = new GLTexture[textures.size()];
                for (int index = 0; index < textures.size(); index++) {
                    TextureSet value = textures.get(index);
                    colors[index] = value.color; depths[index] = value.depth;
                    normals[index] = value.normal;
                }
                return new Section(shader, Map.copyOf(uniforms), depthState, alphaMode,
                        colors, depths, normals, Arrays.copyOf(entries, count), vao,
                        instanceVbo, pickVbo, slotVbo, flagVbo, count);
            } catch (RuntimeException error) {
                for (int index = 1; index < ids.length; index++)
                    deleteBuffer(gl, ids[index]);
                if (vao != 0)
                    gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
                throw error;
            } finally {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
                gl.glBindVertexArray(0);
            }
        }

        int draw(GL3 gl, GLRenderer2D renderer, float[] projection, float[] model,
                GLLighting2D lighting, PickFramebuffer picking,
                boolean renderColor, boolean renderPicking) {
			if (shader != null && shader.isDisposed())
				throw new IllegalStateException(
						"Resident sprite material shader is disposed");
            renderer.validateSpriteDepthState(depthState);
            renderer.applyDepthState(depthState);
            gl.glDepthRange(0, 1);
            bindTextures(gl);
            gl.glBindVertexArray(vao);
            int calls = 0;
			boolean restoreColorBlend = false;
			try {
				if (renderColor) {
					SpriteBatch.configureBlend(gl, alphaMode);
					restoreColorBlend = true;
					ShaderProgram active = shader == null
							? renderer.defaultSpriteProgram() : shader;
					active.use(gl);
					active.setMatrix4(gl, "uProjection", projection);
					active.setMatrix4(gl, "uModel", model);
					SpriteBatch.setSamplers(gl, active);
					SpriteBatch.setLighting(gl, active, lighting);
					for (Map.Entry<String, ShaderUniformValue> uniform : uniforms.entrySet())
						uniform.getValue().apply(gl, active, uniform.getKey());
					gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, count);
					calls++;
					SpriteBatch.restoreBlend(gl, alphaMode);
					restoreColorBlend = false;
				}
				if (renderPicking && preparePickIds(gl, renderer)) {
					picking.drawSprite(gl, vao, count, projection, model,
							colors, depths, normals);
					calls++;
				}
				return calls;
			} finally {
				if (restoreColorBlend)
					SpriteBatch.restoreBlend(gl, alphaMode);
				gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
				gl.glBindVertexArray(0);
				unbindTextures(gl);
				gl.glUseProgram(0);
			}
        }

        private boolean preparePickIds(GL3 gl, GLRenderer2D renderer) {
            pickIds.clear();
            boolean any = false;
            for (GLSpriteBuffer.Entry entry : entries) {
                int id = renderer.createPickRecord(entry.pickId, entry.mouseTarget(),
                        entry.localTransform, entry.mouseOriginX, entry.mouseOriginY);
                pickIds.put(id);
                any |= id != 0;
            }
            if (!any)
                return false;
            pickIds.flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, pickVbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) count * Integer.BYTES,
                    pickIds, GL3.GL_STREAM_DRAW);
            return true;
        }

        private void bindTextures(GL3 gl) {
            for (int slot = 0; slot < colors.length; slot++) {
                colors[slot].bind(gl, slot);
                depths[slot].bind(gl, SpriteBatch.MAX_TEXTURE_SETS + slot);
                normals[slot].bind(gl, SpriteBatch.MAX_TEXTURE_SETS * 2 + slot);
            }
        }

        private void unbindTextures(GL3 gl) {
            for (int unit = 0; unit < SpriteBatch.MAX_TEXTURE_SETS * 3; unit++) {
                gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
                gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            }
            gl.glActiveTexture(GL.GL_TEXTURE0);
        }

        void dispose(GL3 gl) {
            deleteBuffer(gl, flagVbo); flagVbo = 0;
            deleteBuffer(gl, slotVbo); slotVbo = 0;
            deleteBuffer(gl, pickVbo); pickVbo = 0;
            deleteBuffer(gl, instanceVbo); instanceVbo = 0;
            if (vao != 0) {
                gl.glDeleteVertexArrays(1, new int[] { vao }, 0);
                vao = 0;
            }
        }
    }

    private static void floatAttribute(GL3 gl, int location, int count,
            int stride, int floatOffset) {
        gl.glEnableVertexAttribArray(location);
        gl.glVertexAttribPointer(location, count, GL.GL_FLOAT, false, stride,
                (long) floatOffset * Float.BYTES);
        gl.glVertexAttribDivisor(location, 1);
    }

    private static void integerAttribute(GL3 gl, int buffer, int location,
            boolean unsigned, IntBuffer values, int count, int usage) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) count * Integer.BYTES,
                values, usage);
        gl.glEnableVertexAttribArray(location);
        gl.glVertexAttribIPointer(location, 1,
                unsigned ? GL.GL_UNSIGNED_INT : GL2ES2.GL_INT, Integer.BYTES, 0);
        gl.glVertexAttribDivisor(location, 1);
    }

    private static void deleteBuffer(GL3 gl, int buffer) {
        if (buffer != 0)
            gl.glDeleteBuffers(1, new int[] { buffer }, 0);
    }

    private static final class TextureSet {
        final GLTexture color;
        final GLTexture depth;
        final GLTexture normal;

        TextureSet(GLTexture color, GLTexture depth, GLTexture normal) {
            this.color = color;
            this.depth = depth;
            this.normal = normal;
        }
    }
}
