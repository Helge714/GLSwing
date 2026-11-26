package com.vingame.glswing.node;

import java.util.*;

/** Physical port-side placement inside a semantically ordered component. */
final class GLNodePortLayout {
    record Wire(GLNode source, GLNode target, double sx, double sy, double tx, double ty) { }
    record Position(double x, double y) { }
    private record Proposal(double x,double y) { }
    private record Cell(long x,long y) { }

    static Map<GLNode,Position> arrange(List<GLNode> order,List<Wire> wires,double hg,double vg) {
        var incident=new IdentityHashMap<GLNode,List<Wire>>();
        for(var n:order)incident.put(n,new ArrayList<>());
        for(var wire:wires){incident.get(wire.source).add(wire);if(wire.source!=wire.target)incident.get(wire.target).add(wire);}
        var positions=new IdentityHashMap<GLNode,Position>();
        var occupied=new Occupancy(order,hg,vg,positions);
        double bottom=0;
        for(var node:order) {
            var proposals=new ArrayList<Proposal>();
            for(var wire:incident.get(node)) {
                boolean target=wire.target==node;
                var other=target?wire.source:wire.target;var p=positions.get(other);
                if(p==null||other==node)continue;
                int otherSide=target?(other.isMirrored()?-1:1):(other.isMirrored()?1:-1);
                int thisSide=target?(node.isMirrored()?1:-1):(node.isMirrored()?-1:1);
                double otherAnchor=target?wire.sx:wire.tx,thisAnchor=target?wire.tx:wire.sx;
                boolean facing=otherSide!=thisSide;
                // Same-side sockets form a U turn: align their actual outer edges
                // and change row. Facing sockets extend the current horizontal row.
                proposals.add(new Proposal(p.x+otherAnchor-thisAnchor+(facing?otherSide*hg:0),
                    facing?p.y:p.y+other.getHeight()+vg));
            }
            double x=median(proposals.stream().mapToDouble(Proposal::x).toArray(),0);
            double y=median(proposals.stream().mapToDouble(Proposal::y).toArray(),positions.isEmpty()?0:bottom+vg);
            y=occupied.clearY(node,x,y);
            positions.put(node,new Position(x,y));occupied.add(node);
            bottom=Math.max(bottom,y+node.getHeight());
        }
        double left=positions.values().stream().mapToDouble(Position::x).min().orElse(0);
        double top=positions.values().stream().mapToDouble(Position::y).min().orElse(0);
        positions.replaceAll((node,p)->new Position(p.x-left,p.y-top));
        return positions;
    }
    private static double median(double[] values,double fallback) {
        if(values.length==0)return fallback;Arrays.sort(values);int i=values.length/2;
        return values.length%2==1?values[i]:(values[i-1]+values[i])*.5;
    }

    /** Spatial buckets avoid scanning a long graph for each collision check.
     * Exceptionally large rectangles use a bounded-bucket fallback. */
    private static final class Occupancy {
        final double cw,ch,hg,vg;
        final Map<GLNode,Position> positions;
        final Map<Cell,List<GLNode>> cells=new HashMap<>();
        final List<GLNode> large=new ArrayList<>();
        Occupancy(List<GLNode> nodes,double hg,double vg,Map<GLNode,Position> positions) {
            this.hg=hg;this.vg=vg;this.positions=positions;
            cw=Math.max(1,nodes.stream().mapToDouble(GLNode::getWidth).average().orElse(1)+hg);
            ch=Math.max(1,nodes.stream().mapToDouble(GLNode::getHeight).average().orElse(1)+vg);
        }
        private long[] range(GLNode n,double x,double y) {
            return new long[]{(long)Math.floor((x-hg*.5)/cw),(long)Math.floor((x+n.getWidth()+hg*.5)/cw),
                (long)Math.floor((y-vg*.5)/ch),(long)Math.floor((y+n.getHeight()+vg*.5)/ch)};
        }
        private boolean wide(long[] r) {return r[1]-r[0]>32||r[3]-r[2]>32||(r[1]-r[0]+1)*(r[3]-r[2]+1)>256;}
        void add(GLNode n) {
            var p=positions.get(n);var r=range(n,p.x,p.y);
            if(wide(r)){large.add(n);return;}
            for(long x=r[0];x<=r[1];x++)for(long y=r[2];y<=r[3];y++)cells.computeIfAbsent(new Cell(x,y),key->new ArrayList<>()).add(n);
        }
        double clearY(GLNode n,double x,double y) {
            while(true) {
                var range=range(n,x,y);var candidates=new HashSet<GLNode>(large);
                if(wide(range))candidates.addAll(positions.keySet());
                else for(long cx=range[0];cx<=range[1];cx++)for(long cy=range[2];cy<=range[3];cy++)
                    candidates.addAll(cells.getOrDefault(new Cell(cx,cy),List.of()));
                double next=y;
                for(var other:candidates) {
                    var p=positions.get(other);
                    if(x<p.x+other.getWidth()+hg-.001&&p.x<x+n.getWidth()+hg-.001
                            &&y<p.y+other.getHeight()+vg-.001&&p.y<y+n.getHeight()+vg-.001)
                        next=Math.max(next,p.y+other.getHeight()+vg);
                }
                if(next==y)return y;y=next;
            }
        }
    }
}
