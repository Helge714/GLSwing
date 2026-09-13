package com.vingame.glswing.node;

/** Interactive placement transaction created by paste or duplicate commands. */
public interface GLNodePlacement {
	void moveTo(float graphX, float graphY);
	void confirm();
	void cancel();
}
