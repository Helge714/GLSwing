package com.vingame.glswing.ui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.function.Consumer;
import javax.swing.*;
import com.vingame.glswing.component.*;
import com.vingame.glswing.rendering.GLSvgDocument;

/** Temporary screenshot overlays; no captured pixels are persisted. */
final class GLScreenColorSampler {
	private GLScreenColorSampler() { }
	static void start(GLContainer source, GLPipettePreview preview, Consumer<Color> selected, Consumer<Exception> failed) {
		if (!EventQueue.isDispatchThread()) { EventQueue.invokeLater(()->start(source,preview,selected,failed)); return; }
		ArrayList<JWindow> windows=new ArrayList<>();
		KeyboardFocusManager focus=KeyboardFocusManager.getCurrentKeyboardFocusManager();
		KeyEventDispatcher[] dispatcher=new KeyEventDispatcher[1];
		Runnable cleanup=()-> {
			if(dispatcher[0]!=null) focus.removeKeyEventDispatcher(dispatcher[0]);
			for(JWindow window:windows) window.dispose();
		};
		try {
			GLParent parent=source;
			while(parent instanceof GLContainer c && c.getGLParent()!=null) parent=c.getGLParent();
			if(parent instanceof GLFrame frame) parent=frame.getGLCanvas();
			Window owner=parent instanceof Component c?SwingUtilities.getWindowAncestor(c):null;
			for(GraphicsDevice device:GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
				Rectangle bounds=device.getDefaultConfiguration().getBounds();
				BufferedImage capture=new Robot(device).createScreenCapture(bounds);
				JWindow window=new JWindow(owner);
				windows.add(window);
				window.addWindowListener(new WindowAdapter() {
					@Override public void windowClosed(WindowEvent event) {
						if(windows.stream().noneMatch(Window::isDisplayable)) cleanup.run();
					}
				});
				int[] pointer={-1,-1};
				JComponent image=new JComponent() {
					@Override protected void paintComponent(Graphics g) {
						g.drawImage(capture,0,0,getWidth(),getHeight(),null);
						if(pointer[0]<0 || !preview.isVisible()) return;
						Color color=new Color(capture.getRGB(Math.min(capture.getWidth()-1,pointer[0]*capture.getWidth()/Math.max(1,getWidth())),
								Math.min(capture.getHeight()-1,pointer[1]*capture.getHeight()/Math.max(1,getHeight()))));
						paintPreview(g, preview, color, pointer[0], pointer[1], getWidth(), getHeight(),
								source.getGLTheme().values().getColor("palette.surface",Color.DARK_GRAY),
								source.getGLTheme().values().getColor("palette.text",Color.WHITE));
					}
				};
				image.addMouseMotionListener(new MouseMotionAdapter() {
					@Override public void mouseMoved(MouseEvent e) { pointer[0]=e.getX(); pointer[1]=e.getY(); image.repaint(); }
				});
				image.setCursor(eyedropperCursor());
				image.addMouseListener(new MouseAdapter() {
					@Override public void mouseExited(MouseEvent e) { pointer[0]=-1; image.repaint(); }
					@Override public void mousePressed(MouseEvent e) {
						Color color=new Color(capture.getRGB(Math.min(capture.getWidth()-1,
								Math.max(0,e.getX()*capture.getWidth()/Math.max(1,image.getWidth()))),
								Math.min(capture.getHeight()-1,Math.max(0,e.getY()*capture.getHeight()/Math.max(1,image.getHeight())))));
						cleanup.run(); if(e.getButton()==MouseEvent.BUTTON1) selected.accept(color);
					}
				});
				window.add(image); window.setBounds(bounds);
			}
			dispatcher[0]=event->{
				if(event.getID()==KeyEvent.KEY_PRESSED && event.getKeyCode()==KeyEvent.VK_ESCAPE) {
					cleanup.run(); return true;
				} return false;
			};
			focus.addKeyEventDispatcher(dispatcher[0]);
			for(JWindow window:windows) window.setVisible(true);
			if(!windows.isEmpty()) windows.get(0).requestFocus();
		} catch(Exception failure) { cleanup.run(); failed.accept(failure); }
	}

	static void paintPreview(Graphics g, GLPipettePreview preview, Color color,
			int pointerX, int pointerY, int screenWidth, int screenHeight, Color background, Color foreground) {
		if (!preview.isVisible()) return;
		ArrayList<String> lines = new ArrayList<>();
		if (preview.showHex()) lines.add(String.format(java.util.Locale.ROOT,"#%02X%02X%02X",color.getRed(),color.getGreen(),color.getBlue()));
		if (preview.showRgb()) lines.add(String.format(java.util.Locale.ROOT,"RGB %d, %d, %d",color.getRed(),color.getGreen(),color.getBlue()));
		FontMetrics metrics=g.getFontMetrics();
		int textWidth=lines.stream().mapToInt(metrics::stringWidth).max().orElse(0);
		int swatch=preview.showColor()?30:0, gap=swatch>0 && !lines.isEmpty()?8:0;
		int width=12+swatch+gap+textWidth, height=12+Math.max(swatch,lines.size()*metrics.getHeight());
		int x=pointerX+20, y=pointerY+20;
		if(x+width>screenWidth) x=pointerX-width-20;
		if(y+height>screenHeight) y=pointerY-height-20;
		x=Math.max(0,x); y=Math.max(0,y);
		g.setColor(background); g.fillRoundRect(x,y,width,height,8,8);
		g.setColor(foreground); g.drawRoundRect(x,y,width-1,height-1,8,8);
		if(swatch>0) {
			g.setColor(color); g.fillRect(x+6,y+(height-swatch)/2,swatch,swatch);
			g.setColor(foreground); g.drawRect(x+6,y+(height-swatch)/2,swatch-1,swatch-1);
		}
		int baseline=y+(height-lines.size()*metrics.getHeight())/2+metrics.getAscent();
		for(String line:lines) { g.drawString(line,x+6+swatch+gap,baseline); baseline+=metrics.getHeight(); }
	}
	private static Cursor eyedropperCursor() {
		Toolkit toolkit=Toolkit.getDefaultToolkit(); Dimension size=toolkit.getBestCursorSize(32,32);
		if(size.width==0 || size.height==0) return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
		BufferedImage glyph=GLSvgDocument.loadResource("com/vingame/glswing/icons/eyedropper.svg")
				.rasterize(Math.max(1,size.width-4),Math.max(1,size.height-4));
		BufferedImage image=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_ARGB);
		Graphics2D g=image.createGraphics();
		try {
			// The cursor uses the same SVG silhouette as the picker button. A dark
			// one-pixel halo keeps the white icon visible over bright pixels.
			BufferedImage outline=tinted(glyph,Color.BLACK);
			for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)
				if(dx!=0||dy!=0)g.drawImage(outline,2+dx,2+dy,null);
			g.drawImage(glyph,2,2,null);
		} finally {g.dispose();}
		return toolkit.createCustomCursor(image,new Point(Math.round(4*size.width/32f),Math.round(29*size.height/32f)),"GLSwing eyedropper");
	}
	private static BufferedImage tinted(BufferedImage source,Color color) {
		BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
		for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++) {
			int alpha=source.getRGB(x,y)>>>24;
			result.setRGB(x,y,(alpha<<24)|(color.getRGB()&0xffffff));
		}
		return result;
	}
}
