package com.vingame.glswing.node;

import java.awt.event.MouseEvent;

import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLMouseTarget;
import com.vingame.glswing.input.GLMouseWheelEvent;
import com.vingame.glswing.node.model.GLNodeConnectionId;

/** A directed, visually configurable connection between two node ports. */
public final class GLNodeConnection implements GLMouseTarget {
	private final GLNodePort output;
	private final GLNodePort input;
	private GLNodeEditor editor;
	private Float width;
	private GLNodeConnectionId modelId;
	private GLNodeConnectionStatus status = GLNodeConnectionStatus.NORMAL;
	private String diagnosticMessage;

	public GLNodeConnection(GLNodePort output, GLNodePort input) {
		if (output == null || output.getDirection() != GLNodePort.Direction.OUTPUT)
			throw new IllegalArgumentException("Connection output must be an output port");
		if (input == null || input.getDirection() != GLNodePort.Direction.INPUT)
			throw new IllegalArgumentException("Connection input must be an input port");
		this.output = output;
		this.input = input;
	}

	public GLNodePort getOutput() { return output; }
	public GLNodePort getInput() { return input; }
	public GLNodeConnectionId getModelId() { return modelId; }

	/** Associates this visual connection with its stable domain-model identity. */
	public void setModelId(GLNodeConnectionId modelId) {
		this.modelId = modelId;
	}

	public GLNodeConnectionStatus getStatus() { return status; }
	public String getDiagnosticMessage() { return diagnosticMessage; }

	/** Sets the diagnostic decoration without changing the connection's data colors. */
	public void setDiagnostic(GLNodeConnectionStatus status, String message) {
		if (status == null)
			throw new IllegalArgumentException("Connection status required");
		String normalized = message == null || message.isBlank() ? null : message;
		if (this.status == status
				&& java.util.Objects.equals(diagnosticMessage, normalized))
			return;
		this.status = status;
		diagnosticMessage = normalized;
		changed();
	}

	public void clearDiagnostic() {
		setDiagnostic(GLNodeConnectionStatus.NORMAL, null);
	}

	public float getWidth() {
		return width != null ? width
				: editor == null ? 3 : editor.getConnectionWidth();
	}

	public boolean hasExplicitWidth() { return width != null; }
	public boolean isSelected() {
		return editor != null && editor.getSelectedConnection() == this;
	}

	public void setWidth(float width) {
		if (!Float.isFinite(width) || width <= 0)
			throw new IllegalArgumentException(
					"Connection width must be finite and greater than zero");
		if (this.width != null && Float.compare(this.width, width) == 0)
			return;
		this.width = width;
		changed();
	}

	/** Restores the width supplied by the owning editor's current style. */
	public void clearWidth() {
		if (width == null)
			return;
		width = null;
		changed();
	}

	void attachTo(GLNodeEditor editor) {
		if (this.editor != null && this.editor != editor)
			throw new IllegalArgumentException(
					"A node connection cannot belong to multiple editors");
		this.editor = editor;
	}

	void detachFrom(GLNodeEditor editor) {
		if (this.editor == editor)
			this.editor = null;
	}

	@Override
	public void mousePressed(GLMouseEvent event) {
		if (editor == null || event.getButton() != MouseEvent.BUTTON1
				&& event.getButton() != MouseEvent.BUTTON3)
			return;
		boolean contextClick = event.getButton() == MouseEvent.BUTTON3;
		editor.activateConnection(this, event.getModifiersEx(), contextClick);
		if (contextClick)
			editor.showConnectionContextMenu(this, event);
		event.consume();
	}

	@Override
	public void mouseWheelMoved(GLMouseWheelEvent event) {
		if (editor != null)
			editor.mouseWheelMoved(event);
	}

	private void changed() {
		if (editor != null)
			editor.connectionChanged(this);
	}
}
