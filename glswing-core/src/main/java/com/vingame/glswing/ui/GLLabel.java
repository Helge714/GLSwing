package com.vingame.glswing.ui;

import java.awt.Color;

import javax.accessibility.AccessibleRole;

import com.vingame.glswing.layout.GLInsets;
import com.vingame.glswing.layout.GLSize;
import com.vingame.glswing.rendering.GLContext2D;
import com.vingame.glswing.rendering.GLFont;
import com.vingame.glswing.rendering.GLRenderParameters;

/** A lightweight text label with optional read-only selection and theme inheritance. */
public class GLLabel extends com.vingame.glswing.component.GLContainer implements com.vingame.glswing.input.GLKeyTarget {
	public enum Alignment { LEFT, CENTER, RIGHT }
	public enum VerticalAlignment { TOP, CENTER, BOTTOM }

	private String text;
    private boolean selectable;
    private int anchor,caret;
    private float selectionScroll;
    public final boolean isSelectable(){return selectable;}
    /** Enables read-only text selection; disabled by default. */
    public final void setSelectable(boolean value){
        if(selectable==value)return;selectable=value;anchor=caret=0;selectionScroll=0;
        if(!value)clearGLFocus(this);invalidateRecording();
    }
    public final String getSelectedText(){return text.substring(Math.min(anchor,caret),Math.max(anchor,caret));}
    public final void selectAll(){if(selectable)select(0,text.length());}
    public final void select(int start,int end){
        if(!selectable)return;
        anchor=boundary(start);caret=boundary(end);ensureCaretVisible();invalidateRecording();
    }
    private int boundary(int value){
        int i=Math.max(0,Math.min(text.length(),value));
        return i>0&&i<text.length()&&Character.isLowSurrogate(text.charAt(i))&&Character.isHighSurrogate(text.charAt(i-1))?i-1:i;
    }
    public final void copy(){
        if(!selectable||anchor==caret)return;
        try{java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(getSelectedText()),null);}
        catch(IllegalStateException|java.awt.HeadlessException ignored){}
    }
    @Override public boolean isFocusTraversable(){return selectable&&isVisible();}
    private float textX(GLFont font){
        var padding=getGLStyle().padding();float available=Math.max(0,getWidth()-padding.horizontal()),width=font.measureText(text);
        return padding.left()+switch(alignment){case LEFT->0;case CENTER->Math.max(0,(available-width)/2);case RIGHT->Math.max(0,available-width);}-selectionScroll;
    }
    private void ensureCaretVisible(){
        var font=measurementFont(getGLStyle().font());var padding=getGLStyle().padding();float available=Math.max(0,getWidth()-padding.horizontal());
        float x=font.measureText(text.substring(0,caret));
        selectionScroll=Math.max(0,Math.min(selectionScroll,Math.max(0,font.measureText(text)-available)));
        if(x<selectionScroll)selectionScroll=x;else if(x>selectionScroll+available)selectionScroll=x-available;
    }
    private int indexAt(float x){
        var font=measurementFont(getGLStyle().font());float target=x-textX(font);
        int previous=0;float previousWidth=0;
        for(int i=0;i<text.length();){i=text.offsetByCodePoints(i,1);float width=font.measureText(text.substring(0,i));if(target<(previousWidth+width)/2)return previous;previous=i;previousWidth=width;}
        return text.length();
    }
    @Override public void mousePressed(com.vingame.glswing.input.GLMouseEvent e){
        if(!selectable)return;
        if(e.getButton()==java.awt.event.MouseEvent.BUTTON1){requestGLFocus(this);int i=indexAt(e.getLocalX());select(e.getOriginalEvent().isShiftDown()?anchor:i,i);e.consume();}
    }
    @Override public void mouseDragged(com.vingame.glswing.input.GLMouseEvent e){if(selectable){select(anchor,indexAt(e.getLocalX()));e.consume();}}
    @Override public void mouseClicked(com.vingame.glswing.input.GLMouseEvent e){
        if(!selectable)return;
        if(e.getButton()==java.awt.event.MouseEvent.BUTTON1&&e.getOriginalEvent().getClickCount()>1){selectAll();e.consume();}
        if(e.getButton()==java.awt.event.MouseEvent.BUTTON3){
            var host=getGLParent();while(host instanceof com.vingame.glswing.component.GLContainer parent&&parent.getGLParent()!=null)host=parent.getGLParent();
            if(host!=null){var menu=new GLPopupMenu();menu.addItem("Copy",a->{if(anchor==caret)selectAll();copy();});menu.addItem("Select all",a->selectAll());menu.show(host,e.getScreenX(),e.getScreenY());e.consume();}
        }
    }
    @Override public void keyPressed(com.vingame.glswing.input.GLKeyEvent e){
        if(!selectable)return;
        var original=e.getOriginalEvent();int code=e.getKeyCode();boolean command=original.isControlDown()||original.isMetaDown();
        if(command&&code==java.awt.event.KeyEvent.VK_A)selectAll();
        else if(command&&code==java.awt.event.KeyEvent.VK_C)copy();
        else if(code==java.awt.event.KeyEvent.VK_HOME)select(original.isShiftDown()?anchor:0,0);
        else if(code==java.awt.event.KeyEvent.VK_END)select(original.isShiftDown()?anchor:text.length(),text.length());
        else if(code==java.awt.event.KeyEvent.VK_LEFT||code==java.awt.event.KeyEvent.VK_RIGHT){
            boolean left=code==java.awt.event.KeyEvent.VK_LEFT;
            int next=!original.isShiftDown()&&anchor!=caret?(left?Math.min(anchor,caret):Math.max(anchor,caret)):
                left?(caret>0?text.offsetByCodePoints(caret,-1):0):(caret<text.length()?text.offsetByCodePoints(caret,1):caret);
            select(original.isShiftDown()?anchor:next,next);
        }else if(code==java.awt.event.KeyEvent.VK_ESCAPE||command)return;
        e.consume();
    }

	private GLLabelStyle localStyle;
	private GLLabelStyle appliedStyle;
	private final GLRenderParameters textParameters = new GLRenderParameters();
	private Alignment alignment = Alignment.LEFT;
	private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

	public GLLabel(String text) {
		this.text = requireText(text);
		updatePresentation();
	}

	public final String getText() {
		return text;
	}

	public final void setText(String text) {
		text = requireText(text);
		if (this.text.equals(text))
			return;
		String oldText = this.text;
		this.text = text;
        anchor=caret=0;selectionScroll=0;
		fireAccessibleNameChange(oldText, text);
		updatePresentation();
	}

	public final void setGLStyle(GLLabelStyle style) {
		if (style == null)
			throw new IllegalArgumentException("Label style must not be null");
		if (localStyle == style)
			return;
		localStyle = style;
		updatePresentation();
	}

	public final void clearGLStyle() {
		if (localStyle == null)
			return;
		localStyle = null;
		updatePresentation();
	}

	public final GLLabelStyle getGLStyle() {
		return localStyle != null ? localStyle : getGLTheme().labelStyle();
	}
	public final Alignment getAlignment() { return alignment; }
	public final void setAlignment(Alignment alignment) {
		if(alignment==null)throw new IllegalArgumentException("Label alignment must not be null");
		if(this.alignment==alignment)return;this.alignment=alignment;invalidateRecording();
	}
	public final VerticalAlignment getVerticalAlignment() { return verticalAlignment; }
	public final void setVerticalAlignment(VerticalAlignment alignment) {
		if(alignment==null)throw new IllegalArgumentException("Vertical label alignment must not be null");
		if(verticalAlignment==alignment)return;verticalAlignment=alignment;invalidateRecording();
	}

	@Override
	protected void record(GLContext2D context) {
		GLLabelStyle style = getGLStyle();
		GLFont font = getGLFont(style.font());
		GLInsets padding = style.padding();
		String renderedText = selectable?text:GLTextFitter.ellipsize(font, text,
				Math.max(0, getWidth() - padding.horizontal()));
		float available=Math.max(0,getWidth()-padding.horizontal());
		float textWidth=font.measureText(renderedText);
		float x=selectable?textX(font):padding.left()+switch(alignment) {
			case LEFT -> 0; case CENTER -> (available-textWidth)/2; case RIGHT -> available-textWidth;
		};
		float verticalSpace=Math.max(0,getHeight()-padding.vertical()-font.getLineHeight());
		float baseline=padding.top()+switch(verticalAlignment) {
			case TOP -> 0;case CENTER -> verticalSpace/2;case BOTTOM -> verticalSpace;
		}+font.getAscent();
        if(selectable){
            boolean color=context.isColorRenderingEnabled();context.setColorRenderingEnabled(false);
            context.fillRect(0,0,getWidth(),getHeight(),Color.WHITE);context.setColorRenderingEnabled(color);
            context.beginClip(padding.left(),0,Math.max(0,getWidth()-padding.horizontal()),getHeight());
            if(anchor!=caret){float a=font.measureText(text.substring(0,Math.min(anchor,caret))),b=font.measureText(text.substring(0,Math.max(anchor,caret)));
                context.fillRect(x+a,baseline-font.getAscent(),b-a,font.getLineHeight(),getGLTheme().textEditorStyle().selectionColor());}
        }
		context.drawUITextShadow(font, renderedText, x,
				baseline, getGLTheme().textShadow("label"));
		context.withParameters(textParameters, () -> context.drawUIText(font,
				renderedText,
				x, baseline, Color.WHITE));
        if(selectable)context.endClip();
	}

	@Override
	protected String defaultName() {
		return text;
	}
	@Override protected AccessibleRole defaultAccessibleRole() {
		return AccessibleRole.LABEL;
	}

	@Override
	protected boolean isMouseTargetEnabled() {
		return selectable;
	}

	@Override
	protected void themeChanged() {
		if (localStyle == null && !getGLStyle().equals(appliedStyle))
			updatePresentation();
	}

	private void updatePresentation() {
		GLLabelStyle style = getGLStyle();
		appliedStyle = style;
		textParameters.setColor(style.textColor());
		GLFont font = measurementFont(style.font());
		GLInsets padding = style.padding();
		setIntrinsicPreferredGLSize(new GLSize(
				font.measureText(text) + padding.horizontal(),
				font.getLineHeight() + padding.vertical()));
		invalidateRecording();
	}

	private GLFont measurementFont(GLFontSpec specification) {
		return getGLParent() == null ? specification.measurementFont()
				: getGLFont(specification);
	}

	private static String requireText(String text) {
		if (text == null)
			throw new IllegalArgumentException("Label text must not be null");
		return text;
	}
}
