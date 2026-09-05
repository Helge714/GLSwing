package com.vingame.glswing.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.vingame.glswing.component.GLContainer;
import com.vingame.glswing.component.GLParent;
import com.vingame.glswing.layout.GLConstraints.Connection;
import com.vingame.glswing.layout.GLGridLayout;
import com.vingame.glswing.rendering.*;

/** Visual grouping of axis-aligned controls, independent of their input/history.
 * Connections require touching edges and permission on both sides. Nested layout
 * groups contribute their occupied cells, never their bounding rectangle.
 * Convex exterior corners are rounded; concave junctions remain square.
 */
public class GLLayoutGroup extends GLContainer {
	private record Gate(GLLayoutGroup group, Set<Connection> sides) { }
	private record Cell(GLContainer control,float x,float y,float w,float h,Set<Connection> sides,List<Gate> gates) { }
	private record Edge(Cell first,Cell second,float x1,float y1,float x2,float y2) { }
	private List<Cell> previous=List.of();
	private List<Edge> seams=List.of();
	private Area area=new Area();
	private GLPath2D borderPath=new GLPath2D();
	private float lastRadius=-1,lastBorder=-1;
	private boolean highlighted;
	private Area highlightShape=new Area();
	/** Creates a visual group backed by an initially empty grid layout. */
	public GLLayoutGroup() { setGLLayout(new GLGridLayout()); }
	@Override protected void record(GLContext2D context) { }
	@Override protected boolean isMouseTargetEnabled() { return false; }
	/** Recomputes connections after layout; rendering also calls this automatically. */
	public final void refreshConnections() {
		layoutGL();
		List<Cell> cells=new ArrayList<>(); collect(this,0,0,cells,List.of());
		float radius=getGLTheme().values().getFloat("layoutGroup.cornerRadius",getGLTheme().buttonStyle().cornerRadius());
		float border=Math.max(0,getGLTheme().values().getFloat("layoutGroup.borderWidth",getGLTheme().buttonStyle().borderWidth()));
		if(cells.equals(previous) && radius==lastRadius && border==lastBorder) return;
		for(Cell old:previous) old.control.setConnectedShape(this,null);
		previous=List.copyOf(cells); lastRadius=radius; lastBorder=border;
		int[] component=new int[cells.size()]; for(int i=0;i<component.length;i++) component[i]=i;
		List<Edge> edges=new ArrayList<>();
		for(int i=0;i<cells.size();i++) for(int j=i+1;j<cells.size();j++) {
			Edge edge=shared(cells.get(i),cells.get(j));
			if(edge!=null) { edges.add(edge); int from=component[j],to=component[i];
				for(int k=0;k<component.length;k++) if(component[k]==from) component[k]=to; }
		}
		seams=List.copyOf(edges); area=new Area();
		for(int i=0;i<cells.size();i++) {
			Cell c=cells.get(i); float[] radii=new float[4];
			float[] xs={c.x,c.x+c.w,c.x+c.w,c.x}, ys={c.y,c.y,c.y+c.h,c.y+c.h};
			for(int corner=0;corner<4;corner++) {
				int occupied=0;
				for(float dx:new float[]{-.01f,.01f}) for(float dy:new float[]{-.01f,.01f}) {
					for(int k=0;k<cells.size();k++) if(component[k]==component[i] && contains(cells.get(k),xs[corner]+dx,ys[corner]+dy)) {occupied++;break;}
				}
				float r=Math.min(radius,Math.min(c.w,c.h)/2);
				for(int k=0;k<cells.size();k++) if(component[k]==component[i]) {
					Cell other=cells.get(k);
					for(float v:new float[]{other.x,other.x+other.w}) {float d=Math.abs(v-xs[corner]);if(d>.01f) r=Math.min(r,d);}
					for(float v:new float[]{other.y,other.y+other.h}) {float d=Math.abs(v-ys[corner]);if(d>.01f) r=Math.min(r,d);}
				}
				radii[corner]=occupied==1 && r>1?r:0;
			}
			GLCornerRadii shape=new GLCornerRadii(radii[0],radii[1],radii[2],radii[3]);
			c.control.setConnectedShape(this,shape); area.add(new Area(rounded(c,radii)));
		}
		Area outline=border>0?new Area(new BasicStroke(border*2).createStrokedShape(area)):new Area();
		outline.intersect(area); borderPath=path(outline);
	}
	/** Defensive copy of the occupied rounded union, useful for custom backgrounds. */
	public final Area getOutlineShape() { refreshConnections(); return new Area(area); }
	/** Uses the theme focus color for the complete connected outline and its seams. */
	public final void setHighlighted(boolean highlighted) {
		if(this.highlighted==highlighted)return;
		this.highlighted=highlighted;invalidateRecording();repaintGL();
	}
	/** Highlights only the part of the occupied group covered by a local shape. */
	public final void setHighlightShape(java.awt.Shape shape) {
		Area next=shape==null?new Area():new Area(shape);
		if(next.equals(highlightShape)&&!highlighted)return;
		highlighted=false;highlightShape=next;invalidateRecording();repaintGL();
	}
	public final void clearHighlightShape() { setHighlightShape(null); }
	public final boolean isHighlighted() { return highlighted||!highlightShape.isEmpty(); }
	private static void collect(GLLayoutGroup group,float x,float y,List<Cell> result,List<Gate> gates) {
		group.layoutGL();
		for(GLContainer child:group.getGLChildren()) if(child.isVisible()) {
			if(child instanceof GLLayoutGroup nested) {
				List<Gate> next=new ArrayList<>(gates);
				next.add(new Gate(nested,group.getGLConstraints(child).connections()));
				collect(nested,x+child.getX(),y+child.getY(),result,List.copyOf(next));
			} else if(child.getWidth()>0 && child.getHeight()>0) result.add(new Cell(child,x+child.getX(),y+child.getY(),child.getWidth(),child.getHeight(),group.getGLConstraints(child).connections(),gates));
		}
	}
	private static boolean contains(Cell c,float x,float y) { return x>c.x && x<c.x+c.w && y>c.y && y<c.y+c.h; }
	private static boolean permits(Cell a,Cell b,Connection side) {
		if(!a.sides.contains(side)) return false;
		for(Gate gate:a.gates) if(!b.gates.stream().anyMatch(other->other.group==gate.group) && !gate.sides.contains(side)) return false;
		return true;
	}
	private static Edge shared(Cell a,Cell b) {
		float top=Math.max(a.y,b.y),bottom=Math.min(a.y+a.h,b.y+b.h);
		if(bottom>top && Math.abs(a.x+a.w-b.x)<.01f && permits(a,b,Connection.RIGHT) && permits(b,a,Connection.LEFT)) return new Edge(a,b,b.x,top,b.x,bottom);
		if(bottom>top && Math.abs(b.x+b.w-a.x)<.01f && permits(b,a,Connection.RIGHT) && permits(a,b,Connection.LEFT)) return new Edge(a,b,a.x,top,a.x,bottom);
		float left=Math.max(a.x,b.x),right=Math.min(a.x+a.w,b.x+b.w);
		if(right>left && Math.abs(a.y+a.h-b.y)<.01f && permits(a,b,Connection.BOTTOM) && permits(b,a,Connection.TOP)) return new Edge(a,b,left,b.y,right,b.y);
		if(right>left && Math.abs(b.y+b.h-a.y)<.01f && permits(b,a,Connection.BOTTOM) && permits(a,b,Connection.TOP)) return new Edge(a,b,left,a.y,right,a.y);
		return null;
	}
	private static Path2D rounded(Cell c,float[] r) {
		double x=c.x,y=c.y,w=c.w,h=c.h,k=.5522847498;
		Path2D p=new Path2D.Double(); p.moveTo(x+r[0],y); p.lineTo(x+w-r[1],y);
		p.curveTo(x+w-r[1]+k*r[1],y,x+w,y+r[1]-k*r[1],x+w,y+r[1]); p.lineTo(x+w,y+h-r[2]);
		p.curveTo(x+w,y+h-r[2]+k*r[2],x+w-r[2]+k*r[2],y+h,x+w-r[2],y+h); p.lineTo(x+r[3],y+h);
		p.curveTo(x+r[3]-k*r[3],y+h,x,y+h-r[3]+k*r[3],x,y+h-r[3]); p.lineTo(x,y+r[0]);
		p.curveTo(x,y+r[0]-k*r[0],x+r[0]-k*r[0],y,x+r[0],y); p.closePath(); return p;
	}
	private static GLPath2D path(Area area) {
		GLPath2D path=new GLPath2D(); float[] p=new float[6];
		for(PathIterator it=area.getPathIterator(null);!it.isDone();it.next()) switch(it.currentSegment(p)) {
			case PathIterator.SEG_MOVETO -> path.moveTo(p[0],p[1]);
			case PathIterator.SEG_LINETO -> path.lineTo(p[0],p[1]);
			case PathIterator.SEG_QUADTO -> path.quadTo(p[0],p[1],p[2],p[3]);
			case PathIterator.SEG_CUBICTO -> path.cubicTo(p[0],p[1],p[2],p[3],p[4],p[5]);
			case PathIterator.SEG_CLOSE -> path.closePath();
		}
		return path;
	}
	@Override protected void renderChildren(GLContext2D context) {
		if(getGLParent() instanceof GLLayoutGroup) {super.renderChildren(context);return;}
		refreshConnections(); super.renderChildren(context);
		Color border=getGLTheme().values().getColor("layoutGroup.border",getGLTheme().buttonStyle().borderColor());
		context.fillPath(borderPath,border);
		GLStroke old=context.getStroke();
		try { context.setStroke(new GLStroke(lastBorder));
			if(lastBorder>0) for(Edge edge:seams) context.drawUILine(edge.x1,edge.y1,edge.x2,edge.y2,border);
		} finally {context.setStroke(old);}
		Area selected=new Area(area);
		if(!highlighted)selected.intersect(highlightShape);
		if(selected.isEmpty())return;
		Color focus=getGLTheme().values().getColor("layoutGroup.focusBorder",
				getGLTheme().textEditorStyle().focusedBorderColor());
		float focusWidth=Math.max(1.5f,lastBorder);
		Area focusOutline=new Area(new BasicStroke(focusWidth*2).createStrokedShape(selected));
		focusOutline.intersect(selected);context.fillPath(path(focusOutline),focus);
		try {context.setStroke(new GLStroke(focusWidth));
			for(Edge edge:seams)if(selectedCell(edge.first,selected)&&selectedCell(edge.second,selected))
				context.drawUILine(edge.x1,edge.y1,edge.x2,edge.y2,focus);
		} finally {context.setStroke(old);}
	}
	private static boolean selectedCell(Cell cell,Area selected) {
		return selected.contains(cell.x+cell.w/2,cell.y+cell.h/2);
	}
	@Override protected void detached(GLParent formerParent) {
		for(Cell cell:previous) cell.control.setConnectedShape(this,null);
		previous=List.of(); super.detached(formerParent);
	}
}
