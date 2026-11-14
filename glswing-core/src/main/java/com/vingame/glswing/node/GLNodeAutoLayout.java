package com.vingame.glswing.node;

import java.util.*;

/** Deterministic, port-aware layered layout. Only node locations are changed. */
public final class GLNodeAutoLayout {
    /** Optional structural hints; independent of application-specific node types. */
    public enum Role { NORMAL, INPUT_BOUNDARY, OUTPUT_BOUNDARY }
    private GLNodeAutoLayout() { }

    public static void arrange(GLNodeEditor editor) {
        if (!editor.getNodes().isEmpty()) new Layout(editor,false).run();
    }

    /** Align a user's rough rows/columns while retaining left-to-right signal flow. */
    public static void arrangePreservingOrder(GLNodeEditor editor) {
        if (!editor.getNodes().isEmpty()) new Layout(editor,true).run();
    }

    private static final class Vertex {
        final GLNode node;
        final int order;
        final double w,h;
        final Role role;
        final List<Edge> in=new ArrayList<>(),out=new ArrayList<>();
        Block block;
        double x,y,lx,ly;
        Vertex(GLNode node,int order) {
            this.node=node;this.order=order;w=node.getWidth();h=node.getHeight();role=node.getAutoLayoutRole();
        }
    }
    private record Edge(Vertex a,Vertex b,double ax,double ay,double bx,double by) { }
    private static final class Block {
        final List<Vertex> nodes=new ArrayList<>();
        final List<Link> in=new ArrayList<>(),out=new ArrayList<>();
        int order,rank;
        double originalX,originalY;
        double x,y,w,h;
    }
    private record Link(Edge edge,Block a,Block b) {
        double ay(){return edge.a.ly+edge.ay;}
        double by(){return edge.b.ly+edge.by;}
        double ax(){return edge.a.lx+edge.ax;}
        double bx(){return edge.b.lx+edge.bx;}
    }
    private static final class Component {
        final List<Vertex> nodes;
        final List<Block> blocks;
        final List<Link> links=new ArrayList<>();
        final List<List<Block>> layers=new ArrayList<>();
        final int order;
        final boolean boundaryConnected;
        final double boundaryOrder;
        double x,y,w,h;
        Component(List<Vertex> nodes,List<Block> blocks) {
            this.nodes=nodes;this.blocks=blocks;order=nodes.stream().mapToInt(v->v.order).min().orElse(0);
            boundaryConnected=nodes.stream().anyMatch(v->v.in.stream().anyMatch(e->e.a.role!=Role.NORMAL)||v.out.stream().anyMatch(e->e.b.role!=Role.NORMAL));
            boundaryOrder=nodes.stream().flatMap(v->v.in.stream()).filter(e->e.a.role==Role.INPUT_BOUNDARY)
                .mapToDouble(e->e.ay).min().orElseGet(()->nodes.stream().flatMap(v->v.out.stream())
                    .filter(e->e.b.role==Role.OUTPUT_BOUNDARY).mapToDouble(e->e.by).min().orElse(Double.POSITIVE_INFINITY));
        }
    }
    private record Visit(Vertex node,Iterator<Edge> edges) { }
    private record Shelf(double start,double end,double height) { }
    private record Score(int crossings,double length) implements Comparable<Score> {
        public int compareTo(Score other){int c=Integer.compare(crossings,other.crossings);return c!=0?c:Double.compare(length,other.length);}
    }

    private static final class Layout {
        final List<Vertex> nodes=new ArrayList<>();
        final List<Component> components=new ArrayList<>();
        final double hg,vg,aspect,originX,originY;
        final boolean preserve;
        Layout(GLNodeEditor editor,boolean preserve) {
            this.preserve=preserve;
            hg=editor.getHorizontalNodeSpacing();vg=editor.getVerticalNodeSpacing();
            aspect=editor.getWidth()>0&&editor.getHeight()>0?Math.max(.5,Math.min(3,editor.getWidth()/editor.getHeight())):1.6;
            var byNode=new IdentityHashMap<GLNode,Vertex>();
            for(var node:editor.getNodes()){var vertex=new Vertex(node,nodes.size());nodes.add(vertex);byNode.put(node,vertex);}
            originX=nodes.stream().mapToDouble(v->v.node.getX()).min().orElse(0);
            originY=nodes.stream().mapToDouble(v->v.node.getY()).min().orElse(0);
            for(var wire:editor.getConnections()) {
                var a=byNode.get(wire.getOutput().getNode());var b=byNode.get(wire.getInput().getNode());
                if(a==null||b==null)continue;
                var edge=new Edge(a,b,wire.getOutput().getCenterXInNode(),wire.getOutput().getCenterYInNode(),
                    wire.getInput().getCenterXInNode(),wire.getInput().getCenterYInNode());
                a.out.add(edge);b.in.add(edge);
            }
        }
        void run() {
            var remaining=new LinkedHashSet<Vertex>();for(var v:nodes)if(v.role==Role.NORMAL)remaining.add(v);
            while(!remaining.isEmpty()) {
                var part=new ArrayList<Vertex>();var pending=new ArrayDeque<Vertex>();pending.add(remaining.iterator().next());
                while(!pending.isEmpty()) {
                    var v=pending.removeFirst();if(!remaining.remove(v))continue;part.add(v);
                    for(var e:v.out)if(e.b.role==Role.NORMAL)pending.addLast(e.b);
                    for(var e:v.in)if(e.a.role==Role.NORMAL)pending.addLast(e.a);
                }
                part.sort(Comparator.comparingInt(v->v.order));
                var component=new Component(part,strongComponents(part));arrangeComponent(component);components.add(component);
            }
            components.sort(Comparator.comparingDouble((Component c)->c.boundaryOrder).thenComparingInt(c->c.order));
            pack();
            double inputWidth=nodes.stream().filter(v->v.role==Role.INPUT_BOUNDARY).mapToDouble(v->v.w).max().orElse(0);
            boolean inputs=nodes.stream().anyMatch(v->v.role==Role.INPUT_BOUNDARY);
            double start=inputs?inputWidth+hg:0;
            for(var c:components)for(var v:c.nodes){v.x=start+c.x+v.block.x+v.lx;v.y=c.y+v.block.y+v.ly;}
            double right=nodes.stream().filter(v->v.role==Role.NORMAL).mapToDouble(v->v.x+v.w).max().orElse(inputWidth);
            placeBoundaries(Role.INPUT_BOUNDARY,0);
            placeBoundaries(Role.OUTPUT_BOUNDARY,components.isEmpty()?(inputs?inputWidth+hg:0):right+hg);
            double minX=nodes.stream().mapToDouble(v->v.x).min().orElse(0),minY=nodes.stream().mapToDouble(v->v.y).min().orElse(0);
            for(var v:nodes)v.node.setLocation((float)(originX+v.x-minX),(float)(originY+v.y-minY));
        }

        /** Iterative Kosaraju traversal avoids call-stack limits on long graphs. */
        List<Block> strongComponents(List<Vertex> part) {
            var seen=new HashSet<Vertex>();var finished=new ArrayList<Vertex>();var stack=new ArrayDeque<Visit>();
            for(var start:part) {
                if(!seen.add(start))continue;stack.push(new Visit(start,start.out.iterator()));
                while(!stack.isEmpty()) {
                    var visit=stack.peek();
                    if(!visit.edges.hasNext()){finished.add(visit.node);stack.pop();continue;}
                    var next=visit.edges.next().b;
                    if(next.role==Role.NORMAL&&seen.add(next))stack.push(new Visit(next,next.out.iterator()));
                }
            }
            var result=new ArrayList<Block>();var pending=new ArrayDeque<Vertex>();
            for(int i=finished.size()-1;i>=0;i--) {
                var start=finished.get(i);if(start.block!=null)continue;var block=new Block();pending.push(start);start.block=block;
                while(!pending.isEmpty()) {
                    var v=pending.pop();block.nodes.add(v);
                    for(var e:v.in)if(e.a.role==Role.NORMAL&&e.a.block==null){e.a.block=block;pending.push(e.a);}
                }
                block.nodes.sort(Comparator.comparingInt(v->v.order));block.order=block.nodes.get(0).order;
                block.originalX=block.nodes.stream().mapToDouble(v->v.node.getX()).min().orElse(0);
                block.originalY=block.nodes.stream().mapToDouble(v->v.node.getY()).min().orElse(0);
                arrangeCycle(block);result.add(block);
            }
            result.sort(Comparator.comparingInt(b->b.order));return result;
        }

        /** A feedback region stays contiguous; return vertices occupy a lower lane. */
        void arrangeCycle(Block block) {
            if(block.nodes.size()==1){var v=block.nodes.get(0);block.w=v.w;block.h=v.h;return;}
            var remaining=new LinkedHashSet<>(block.nodes);var ordered=new ArrayList<Vertex>();var degree=new HashMap<Vertex,Integer>();
            for(var v:remaining)degree.put(v,(int)v.in.stream().filter(e->e.a!=v&&e.a.block==block).count());
            while(!remaining.isEmpty()) {
                var next=remaining.stream().filter(v->degree.get(v)==0).findFirst().orElseGet(()->remaining.stream()
                    .max(Comparator.comparingInt((Vertex v)->(v.in.stream().anyMatch(e->e.a.block!=block)?1000000:0)
                        +(int)v.out.stream().filter(e->remaining.contains(e.b)&&e.b!=v).count()-degree.get(v))
                        .thenComparingInt(v->-v.order)).orElseThrow());
                remaining.remove(next);ordered.add(next);for(var e:next.out)if(remaining.contains(e.b))degree.merge(e.b,-1,Integer::sum);
            }
            var index=new HashMap<Vertex,Integer>();for(int i=0;i<ordered.size();i++)index.put(ordered.get(i),i);
            var returns=new HashSet<Vertex>();
            for(var v:ordered)if(v.out.stream().anyMatch(e->e.b!=v&&e.b.block==block&&index.get(e.b)<index.get(v)))returns.add(v);
            double x=0,minY=0,bottom=0;
            for(var v:ordered) {
                v.lx=x;x+=v.w+hg;
                if(!returns.contains(v)) {
                    var targets=v.in.stream().filter(e->e.a.block==block&&!returns.contains(e.a)&&index.get(e.a)<index.get(v))
                        .mapToDouble(e->e.a.ly+e.ay-e.by).toArray();
                    v.ly=median(targets,0)/(block.nodes.size()>2?3:1);minY=Math.min(minY,v.ly);bottom=Math.max(bottom,v.ly+v.h);
                }
            }
            for(var v:ordered)v.ly=returns.contains(v)?bottom-minY+vg:v.ly-minY;
            block.w=x-hg;block.h=ordered.stream().mapToDouble(v->v.ly+v.h).max().orElse(0);
        }

        void arrangeComponent(Component c) {
            for(var v:c.nodes)for(var e:v.out)if(e.b.role==Role.NORMAL&&e.a.block!=e.b.block) {
                var link=new Link(e,e.a.block,e.b.block);c.links.add(link);link.a.out.add(link);link.b.in.add(link);
            }
            var degree=new HashMap<Block,Integer>();var ready=new PriorityQueue<Block>(Comparator.comparingInt(b->b.order));
            for(var b:c.blocks){degree.put(b,b.in.size());if(b.in.isEmpty())ready.add(b);}
            var order=new ArrayList<Block>();
            while(!ready.isEmpty()){var b=ready.remove();order.add(b);for(var e:b.out){e.b.rank=Math.max(e.b.rank,b.rank+1);if(degree.merge(e.b,-1,Integer::sum)==0)ready.add(e.b);}}
            // Move short modulation/source branches close to their first consumer.
            for(int i=order.size()-1;i>=0;i--){var b=order.get(i);if(!b.out.isEmpty())b.rank=b.out.stream().mapToInt(e->e.b.rank-1).min().orElse(b.rank);}
            var layers=new TreeMap<Integer,List<Block>>();for(var b:c.blocks)layers.computeIfAbsent(b.rank,k->new ArrayList<>()).add(b);c.layers.addAll(layers.values());
            if(preserve){
                seedRows(c);place(c);compactX(c);
                c.w=c.blocks.stream().mapToDouble(b->b.x+b.w).max().orElse(0);
                c.h=c.blocks.stream().mapToDouble(b->b.y+b.h).max().orElse(0);
                return;
            }
            place(c);var best=score(c);var bestOrder=copy(c.layers);
            for(int pass=0;pass<8;pass++) {
                final boolean forward=pass%2==0;
                var sweep=new ArrayList<>(c.layers);if(!forward)Collections.reverse(sweep);
                for(var layer:sweep) {
                    var targets=new HashMap<Block,Double>();
                    for(var b:layer)targets.put(b,median((forward?b.in:b.out).stream()
                        .mapToDouble(e->forward?e.a.y+e.ay()-e.by():e.b.y+e.by()-e.ay()).toArray(),b.y));
                    layer.sort(Comparator.comparingDouble((Block b)->targets.get(b)).thenComparingInt(b->b.order));
                }
                place(c);var candidate=score(c);if(candidate.compareTo(best)<0){best=candidate;bestOrder=copy(c.layers);}
            }
            restore(c.layers,bestOrder);
            int budget=c.links.size()<=256?128:0;
            for(int pass=0;pass<2&&budget>0;pass++)for(var layer:c.layers)for(int i=0;i+1<layer.size()&&budget-->0;i++) {
                Collections.swap(layer,i,i+1);place(c);var candidate=score(c);
                if(candidate.compareTo(best)<0)best=candidate;else Collections.swap(layer,i,i+1);
            }
            place(c);compactX(c);
            c.w=c.blocks.stream().mapToDouble(b->b.x+b.w).max().orElse(0);c.h=c.blocks.stream().mapToDouble(b->b.y+b.h).max().orElse(0);
        }

        // Position is an ordering hint, not a global grid constraint. Separate
        // ranks may reuse vertical space; original X must not create extra ranks.
        void seedRows(Component c) {
            for(var layer:c.layers)layer.sort(Comparator.comparingDouble((Block b)->b.originalY)
                .thenComparingDouble(b->b.originalX).thenComparingInt(b->b.order));
        }
        void place(Component c) {
            double x=0;
            for(var layer:c.layers){double y=0,width=0;for(var b:layer){b.x=x;b.y=y;y+=b.h+vg;width=Math.max(width,b.w);}x+=width+hg;}
            for(int pass=0;pass<12;pass++) {
                var sweep=new ArrayList<>(c.layers);if(pass%2==1)Collections.reverse(sweep);
                for(var layer:sweep)compactY(layer,c.layers.size()>2?.05:1);
                double top=c.blocks.stream().mapToDouble(b->b.y).min().orElse(0);for(var b:c.blocks)b.y-=top;
            }
        }

        /** Weighted isotonic projection keeps row order and theme clearance. */
        void compactY(List<Block> layer,double portAlignment) {
            int n=layer.size(),pools=0;double[] offset=new double[n],mean=new double[n],weight=new double[n];int[] count=new int[n];
            for(int i=0;i<n;i++) {
                var b=layer.get(i);if(i>0)offset[i]=offset[i-1]+layer.get(i-1).h+vg;
                double sum=0;for(var e:b.in)sum+=e.a.y+portAlignment*(e.ay()-e.by());for(var e:b.out)sum+=e.b.y+portAlignment*(e.by()-e.ay());
                double degree=b.in.size()+b.out.size();mean[pools]=(degree==0?b.y:sum/degree)-offset[i];weight[pools]=Math.max(1,degree);count[pools]=1;pools++;
                while(pools>1&&mean[pools-2]>mean[pools-1]){int a=pools-2,z=pools-1;mean[a]=(mean[a]*weight[a]+mean[z]*weight[z])/(weight[a]+weight[z]);weight[a]+=weight[z];count[a]+=count[z];pools--;}
            }
            int i=0;for(int p=0;p<pools;p++)for(int j=0;j<count[p];j++){layer.get(i).y=mean[p]+offset[i];i++;}
        }

        void compactX(Component c) {
            var order=new ArrayList<>(c.blocks);order.sort(Comparator.comparingInt((Block b)->b.rank).thenComparingDouble(b->b.y).thenComparingInt(b->b.order));
            for(var b:order) {
                if(b.in.isEmpty()||b.out.size()>b.in.size())continue;
                double x=b.in.stream().mapToDouble(e->e.a.x+e.a.w+hg).max().orElse(0);
                var obstacles=new ArrayList<>(c.blocks);obstacles.sort(Comparator.comparingDouble(other->other.x));
                for(var other:obstacles)if(other!=b&&b.y<other.y+other.h+vg-.001&&other.y<b.y+b.h+vg-.001
                    &&x<other.x+other.w+hg-.001&&other.x<x+b.w+hg-.001)x=other.x+other.w+hg;
                if(x<b.x)b.x=x;
            }
        }

        Score score(Component c) {
            double length=0;for(var e:c.links)length+=Math.abs(e.a.x+e.ax()-e.b.x-e.bx())+Math.abs(e.a.y+e.ay()-e.b.y-e.by());
            // A small wire-length increase is preferable to a steadily rising
            // chain. Port distance still matters; horizontal node flow matters too.
            for(var e:c.links)length+=.75*Math.abs(e.a.y+e.edge.a.ly-e.b.y-e.edge.b.ly);
            int crossings=0,stride=Math.max(1,(c.links.size()+255)/256);
            for(int i=0;i<c.links.size();i+=stride)for(int j=i+stride;j<c.links.size();j+=stride)if(crosses(c.links.get(i),c.links.get(j)))crossings++;
            double height=c.blocks.stream().mapToDouble(b->b.y+b.h).max().orElse(0);
            return new Score(crossings,length+.05*height);
        }
        boolean crosses(Link a,Link b) {
            double ax=a.a.x+a.ax(),ay=a.a.y+a.ay(),bx=a.b.x+a.bx(),by=a.b.y+a.by();
            double cx=b.a.x+b.ax(),cy=b.a.y+b.ay(),dx=b.b.x+b.bx(),dy=b.b.y+b.by();
            return orient(ax,ay,bx,by,cx,cy)*orient(ax,ay,bx,by,dx,dy)<-.001
                &&orient(cx,cy,dx,dy,ax,ay)*orient(cx,cy,dx,dy,bx,by)<-.001;
        }

        void pack() {
            if(components.isEmpty())return;
            if(preserve){packRows();return;}
            double area=components.stream().mapToDouble(c->(c.w+hg)*(c.h+vg)).sum();
            double widest=components.stream().mapToDouble(c->c.w).max().orElse(0),natural=Math.sqrt(area*aspect),best=Double.POSITIVE_INFINITY;
            double selected=widest;
            var widths=new TreeSet<Double>();widths.add(widest);
            for(double scale:new double[]{.65,.8,1,1.2,1.5,2})widths.add(Math.max(widest,natural*scale));
            double prefix=0;for(int i=0;i<Math.min(64,components.size());i++){prefix+=components.get(i).w+(i==0?0:hg);widths.add(Math.max(widest,prefix));}
            for(double width:widths) {
                double[] size=shelf(width);double cost=size[0]*size[1]*(1+.4*Math.abs(Math.log(Math.max(.001,size[0]/Math.max(1,size[1]))/aspect)));
                if(cost<best){best=cost;selected=width;}
            }
            shelf(selected);
        }
        void packRows(){
            components.sort(Comparator.comparingDouble((Component c)->originalY(c)).thenComparingDouble(c->originalX(c)).thenComparingInt(c->c.order));
            var row=new ArrayList<Component>();Component anchor=null;double top=0;
            for(var c:components){
                if(anchor!=null&&(anchor.boundaryConnected||c.boundaryConnected||originalY(c)-originalY(anchor)>=Math.max(1,Math.min(originalHeight(anchor),originalHeight(c))*.5))){
                    top=placeRow(row,top);row.clear();anchor=null;
                }
                if(anchor==null)anchor=c;row.add(c);
            }
            placeRow(row,top);
        }
        double placeRow(List<Component> row,double top){
            row.sort(Comparator.comparingDouble((Component c)->originalX(c)).thenComparingInt(c->c.order));
            double left=0,height=0;for(var c:row){c.x=left;c.y=top;left+=c.w+hg;height=Math.max(height,c.h);}return top+height+vg;
        }
        double originalX(Component c){return c.nodes.stream().mapToDouble(v->v.node.getX()).min().orElse(0);}
        double originalY(Component c){return c.nodes.stream().mapToDouble(v->v.node.getY()).min().orElse(0);}
        double originalHeight(Component c){return c.nodes.stream().mapToDouble(v->v.node.getY()+v.h).max().orElse(0)-originalY(c);}
        /** Bottom-left skyline packing also fills space beside taller components. */
        double[] shelf(double width) {
            var skyline=new ArrayList<Shelf>();skyline.add(new Shelf(0,width+hg,0));
            double w=0,h=0;
            for(var c:components) {
                double x=0,y=Double.POSITIVE_INFINITY;
                if(c.boundaryConnected)y=skyline.stream().mapToDouble(Shelf::height).max().orElse(0);
                else {
                    // Sliding interval maximum: examine each skyline span once,
                    // rather than rescanning the entire skyline for each corner.
                    int right=0;var high=new ArrayDeque<Shelf>();
                    for(var candidate:skyline) {
                        double left=candidate.start;if(left+c.w>width+.001)break;
                        while(right<skyline.size()&&skyline.get(right).start<left+c.w+hg-.001){
                            var span=skyline.get(right++);while(!high.isEmpty()&&high.peekLast().height<=span.height)high.removeLast();high.addLast(span);
                        }
                        while(!high.isEmpty()&&high.peekFirst().end<=left+.001)high.removeFirst();
                        double top=high.isEmpty()?0:high.peekFirst().height;
                        if(top<y-.001){x=left;y=top;}
                    }
                }
                c.x=x;c.y=y;w=Math.max(w,x+c.w);h=Math.max(h,y+c.h);
                // Boundary branches keep full-width lanes, protecting their wires.
                double end=c.boundaryConnected?width+hg:x+c.w+hg;
                var replaced=new ArrayList<Shelf>();
                for(var span:skyline){
                    if(span.end<=x||span.start>=end)replaced.add(span);
                    else {if(span.start<x)replaced.add(new Shelf(span.start,x,span.height));if(span.end>end)replaced.add(new Shelf(end,span.end,span.height));}
                }
                replaced.add(new Shelf(x,end,y+c.h+vg));replaced.sort(Comparator.comparingDouble(Shelf::start));skyline.clear();
                for(var span:replaced){
                    if(!skyline.isEmpty()&&Math.abs(skyline.get(skyline.size()-1).height-span.height)<.001){var before=skyline.remove(skyline.size()-1);skyline.add(new Shelf(before.start,span.end,Math.max(before.height,span.height)));}
                    else skyline.add(span);
                }
            }
            return new double[]{w,h};
        }
        void placeBoundaries(Role role,double x) {
            var boundary=nodes.stream().filter(v->v.role==role).toList();double bottom=0;
            for(var v:boundary) {
                double[] targets=(role==Role.INPUT_BOUNDARY?v.out:v.in).stream()
                    .mapToDouble(e->role==Role.INPUT_BOUNDARY?e.b.y+e.by-e.ay:e.a.y+e.ay-e.by).toArray();
                v.x=x;v.y=Math.max(bottom,median(targets,0));bottom=v.y+v.h+vg;
            }
        }
    }
    private static double orient(double ax,double ay,double bx,double by,double cx,double cy){return (bx-ax)*(cy-ay)-(by-ay)*(cx-ax);}
    private static double median(double[] values,double fallback){if(values.length==0)return fallback;Arrays.sort(values);int i=values.length/2;return values.length%2==1?values[i]:(values[i-1]+values[i])*.5;}
    private static List<List<Block>> copy(List<List<Block>> layers){var result=new ArrayList<List<Block>>();for(var layer:layers)result.add(new ArrayList<>(layer));return result;}
    private static void restore(List<List<Block>> target,List<List<Block>> source){for(int i=0;i<target.size();i++){target.get(i).clear();target.get(i).addAll(source.get(i));}}
}
