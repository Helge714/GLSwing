package com.vingame.glswing.node.binding;
/** World-space viewport center. Zoom belongs to the shared editor, not individual graphs. */
public record GLNodeCameraState(float x,float y){
 public GLNodeCameraState{if(!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Finite camera position required");}
}
