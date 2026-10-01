package com.vingame.glswing.node;

import java.util.*;

/** Deterministic left-to-right layout, using actual bounds and edge-to-edge gaps. */
public final class GLNodeAutoLayout {
    private GLNodeAutoLayout() { }
    public static void arrange(GLNodeEditor editor) {
        var nodes=new ArrayList<>(editor.getNodes());
        if(nodes.isEmpty())return;
        nodes.sort(Comparator.comparingDouble(GLNode::getY).thenComparingDouble(GLNode::getX));
        var outgoing=new LinkedHashMap<GLNode,Set<GLNode>>();
        var incoming=new HashMap<GLNode,Integer>();var rank=new HashMap<GLNode,Integer>();
        for(var n:nodes){outgoing.put(n,new LinkedHashSet<>());incoming.put(n,0);rank.put(n,0);}
        for(var c:editor.getConnections()){
            var a=c.getOutput().getNode();var b=c.getInput().getNode();
            if(a!=b&&outgoing.containsKey(a)&&outgoing.containsKey(b)&&outgoing.get(a).add(b))incoming.merge(b,1,Integer::sum);
        }
        var remaining=new LinkedHashSet<>(nodes);
        while(!remaining.isEmpty()){
            // Break a feedback cycle deterministically without changing its wiring.
            var next=remaining.stream().filter(n->incoming.get(n)==0).findFirst().orElseGet(()->remaining.stream().min(Comparator.comparingDouble(GLNode::getX)).orElseThrow());
            remaining.remove(next);
            for(var child:outgoing.get(next))if(remaining.contains(child)){
                rank.put(child,Math.max(rank.get(child),rank.get(next)+1));incoming.merge(child,-1,Integer::sum);
            }
        }
        var columns=new TreeMap<Integer,List<GLNode>>();
        for(var n:nodes)columns.computeIfAbsent(rank.get(n),ignored->new ArrayList<>()).add(n);
        float x=(float)nodes.stream().mapToDouble(GLNode::getX).min().orElse(0);
        float top=(float)nodes.stream().mapToDouble(GLNode::getY).min().orElse(0);
        float horizontal=editor.getHorizontalNodeSpacing(),vertical=editor.getVerticalNodeSpacing();
        for(var column:columns.values()){
            float y=top,width=0;
            for(var n:column){n.setLocation(x,y);y+=n.getHeight()+vertical;width=Math.max(width,n.getWidth());}
            x+=width+horizontal;
        }
    }
}
