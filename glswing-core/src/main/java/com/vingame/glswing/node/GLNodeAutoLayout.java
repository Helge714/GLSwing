package com.vingame.glswing.node;

import java.util.*;

/** Layered, deterministic layout with crossing reduction and compact connected components. */
public final class GLNodeAutoLayout {
 private GLNodeAutoLayout(){}
 public static void arrange(GLNodeEditor editor){
  var nodes=new ArrayList<>(editor.getNodes());if(nodes.isEmpty())return;
  Comparator<GLNode> original=Comparator.comparingDouble(GLNode::getY).thenComparingDouble(GLNode::getX);
  nodes.sort(original);var outgoing=new LinkedHashMap<GLNode,Set<GLNode>>();var adjacent=new LinkedHashMap<GLNode,Set<GLNode>>();
  for(var n:nodes){outgoing.put(n,new LinkedHashSet<>());adjacent.put(n,new LinkedHashSet<>());}
  for(var edge:editor.getConnections()){
   var a=edge.getOutput().getNode();var b=edge.getInput().getNode();if(a==b||!adjacent.containsKey(a)||!adjacent.containsKey(b))continue;
   outgoing.get(a).add(b);adjacent.get(a).add(b);adjacent.get(b).add(a);
  }
  float left=(float)nodes.stream().mapToDouble(GLNode::getX).min().orElse(0),top=(float)nodes.stream().mapToDouble(GLNode::getY).min().orElse(0);
  var remaining=new LinkedHashSet<>(nodes);
  while(!remaining.isEmpty()){
   var component=new ArrayList<GLNode>();var queue=new ArrayDeque<GLNode>();queue.add(remaining.iterator().next());
   while(!queue.isEmpty()){var n=queue.removeFirst();if(!remaining.remove(n))continue;component.add(n);queue.addAll(adjacent.get(n));}
   component.sort(original);top=arrangeComponent(editor,component,outgoing,adjacent,left,top)+editor.getVerticalNodeSpacing()*2;
  }
 }
 private static float arrangeComponent(GLNodeEditor editor,List<GLNode> nodes,Map<GLNode,Set<GLNode>> outgoing,Map<GLNode,Set<GLNode>> adjacent,float left,float top){
  var rank=new HashMap<GLNode,Integer>();var incoming=new HashMap<GLNode,Integer>();for(var n:nodes){rank.put(n,0);incoming.put(n,0);}
  for(var n:nodes)for(var target:outgoing.get(n))incoming.merge(target,1,Integer::sum);
  var pending=new LinkedHashSet<>(nodes);
  while(!pending.isEmpty()){
   // Keep feedback wiring; break its ordering constraint deterministically.
   var next=pending.stream().filter(n->incoming.get(n)==0).findFirst().orElseGet(()->pending.stream().min(Comparator.comparingDouble(GLNode::getX)).orElseThrow());pending.remove(next);
   for(var target:outgoing.get(next))if(pending.contains(target)){rank.put(target,Math.max(rank.get(target),rank.get(next)+1));incoming.merge(target,-1,Integer::sum);}
  }
  var layers=new TreeMap<Integer,List<GLNode>>();for(var n:nodes)layers.computeIfAbsent(rank.get(n),key->new ArrayList<>()).add(n);
  var ordered=new ArrayList<>(layers.values());var positions=new HashMap<GLNode,Double>();
  for(int pass=0;pass<8;pass++){
   for(var layer:ordered)for(int i=0;i<layer.size();i++)positions.put(layer.get(i),(i+.5)/layer.size());
   var sweep=new ArrayList<>(ordered);if(pass%2==1)Collections.reverse(sweep);
   final boolean forward=pass%2==0;
   for(var layer:sweep){
    var scores=new HashMap<GLNode,Double>();
    for(var n:layer)scores.put(n,adjacent.get(n).stream().filter(other->forward?rank.get(other)<rank.get(n):rank.get(other)>rank.get(n)).mapToDouble(positions::get).average().orElse(positions.get(n)));
    layer.sort(Comparator.comparingDouble(scores::get));for(int i=0;i<layer.size();i++)positions.put(layer.get(i),(i+.5)/layer.size());
   }
  }
  float x=left;
  for(var layer:ordered){float y=top,width=0;for(var n:layer){n.setLocation(x,y);y+=n.getHeight()+editor.getVerticalNodeSpacing();width=Math.max(width,n.getWidth());}x+=width+editor.getHorizontalNodeSpacing();}
  for(int pass=0;pass<8;pass++){
   var sweep=new ArrayList<>(ordered);if(pass%2==1)Collections.reverse(sweep);
   for(var layer:sweep)compact(layer,adjacent,top,editor.getVerticalNodeSpacing());
  }
  return (float)nodes.stream().mapToDouble(n->n.getY()+n.getHeight()).max().orElse(top);
 }
 private static void compact(List<GLNode> layer,Map<GLNode,Set<GLNode>> adjacent,float top,float gap){
  int count=layer.size();double[] offset=new double[count],mean=new double[count];int[] size=new int[count];int blocks=0;
  for(int i=0;i<count;i++){
   var n=layer.get(i);if(i>0)offset[i]=offset[i-1]+layer.get(i-1).getHeight()+gap;
   double target=adjacent.get(n).stream().mapToDouble(other->other.getY()+other.getHeight()*.5).average().orElse(n.getY()+n.getHeight()*.5)-n.getHeight()*.5;
   mean[blocks]=target-offset[i];size[blocks]=1;blocks++;
   // Isotonic projection minimizes vertical edge displacement without overlaps.
   while(blocks>1&&mean[blocks-2]>mean[blocks-1]){int a=blocks-2,b=blocks-1;mean[a]=(mean[a]*size[a]+mean[b]*size[b])/(size[a]+size[b]);size[a]+=size[b];blocks--;}
  }
  int index=0;for(int b=0;b<blocks;b++)for(int j=0;j<size[b];j++){var n=layer.get(index);n.setLocation(n.getX(),(float)(Math.max(top,mean[b])+offset[index]));index++;}
 }
}
