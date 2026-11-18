package com.vingame.glswing.node;
import java.util.*;
import java.util.function.Function;
import com.vingame.glswing.node.binding.*;
/** Snapshot policy for projected nodes, auxiliary boundaries and deferred camera restoration. */
final class GLNodeWorkspaceViews {
    private GLNodeWorkspaceViews(){}
    static GLNodeEditorViewState capture(GLNodeEditorViewState projected,GLNodeEditorViewState previous,
            List<GLNode> nodes,Function<GLNode,String> boundaryId,boolean restoreCamera) {
        Map<String,GLNodeViewState> auxiliary=new LinkedHashMap<>(previous==null?Map.of():previous.auxiliaryNodes());
        for(var node:nodes){String id=boundaryId.apply(node);if(id!=null)auxiliary.put(id,GLNodeViewState.capture(node));}
        return new GLNodeEditorViewState(projected.nodes(),auxiliary,
            (restoreCamera||projected.camera()==null)&&previous!=null?previous.camera():projected.camera());
    }
}
