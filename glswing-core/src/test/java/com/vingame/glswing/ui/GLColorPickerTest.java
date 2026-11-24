package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Color;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import com.vingame.glswing.history.GLHistoryManager;
import com.vingame.glswing.input.GLMouseEvent;
import com.vingame.glswing.input.GLPickResult;

class GLColorPickerTest {
	@Test void pipettePreviewContentAndVisibility() {
		assertTrue(GLPipettePreview.DEFAULT.isVisible());
		assertFalse(GLPipettePreview.NONE.isVisible());
		assertFalse(new GLPipettePreview(true,false,false,false).isVisible());
		for(int mask=0;mask<16;mask++) {
			GLPipettePreview preview=new GLPipettePreview((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0);
			var image=new java.awt.image.BufferedImage(320,160,java.awt.image.BufferedImage.TYPE_INT_ARGB);
			var graphics=image.createGraphics();
			try { GLScreenColorSampler.paintPreview(graphics,preview,Color.RED,310,150,320,160,Color.BLACK,Color.WHITE); }
			finally { graphics.dispose(); }
			boolean painted=false;
			for(int y=0;y<160;y++) for(int x=0;x<320;x++) painted |= image.getRGB(x,y)!=0;
			assertEquals(preview.isVisible(),painted);
		}
		GLColorPicker picker=new GLColorPicker(); picker.setPipettePreview(GLPipettePreview.NONE);
		assertEquals(GLPipettePreview.NONE,picker.getPipettePreview());
		GLColorChooser chooser=new GLColorChooser(); chooser.setPipettePreview(GLPipettePreview.NONE);
		assertEquals(GLPipettePreview.NONE,chooser.getPipettePreview());
		assertThrows(NullPointerException.class,()->picker.setPipettePreview(null));
	}
	@Test void modelsRoundTripAndProduceThemeExpressions() {
		for(GLColorModel model:GLColorModel.values()) for(Color color:new Color[] {
				Color.RED,Color.GREEN,Color.BLUE,Color.BLACK,Color.WHITE,new Color(84,120,181,97)}) {
			double[] channels=model.channels(color);
			assertEquals(color,model.color(channels,color.getAlpha()/255d),model.name());
			assertEquals(color,GLThemeValues.parseColor(model.format(channels,color.getAlpha()/255d)),model.name());
		}
	}
	@Test void modelSwitchDoesNotChangeColorOrNotify() {
		Color original=new Color(80,121,179,123); GLColorPicker picker=new GLColorPicker(original);
		int[] notifications={0}; picker.addColorChangeListener(c->notifications[0]++);
		for(GLColorModel model:GLColorModel.values()) { picker.setColorModel(model); assertEquals(original,picker.getColor()); }
		assertEquals(0,notifications[0]);
	}
	@Test void gamutAndAlpha() {
		assertFalse(GLColorModel.OKLCH.isInGamut(new double[]{.7,.4,140}));
		assertTrue(GLColorModel.RGB.isInGamut(new double[]{84,120,181}));
		assertEquals(0,GLColorModel.HSV.color(new double[]{120,1,1},0).getAlpha());
		assertEquals(Color.GREEN,GLColorModel.HSV.color(new double[]{120,1,1},1));
	}
	@Test void channelClickAndUndoSurviveModelSwitch() {
		GLColorPicker picker=new GLColorPicker(Color.BLACK); picker.setBounds(0,0,480,340);
		GLHistoryManager history=new GLHistoryManager(); picker.setGLHistoryManager(history);
		GLColorSlider red=descendants(picker).stream().filter(GLColorSlider.class::isInstance)
				.map(GLColorSlider.class::cast).findFirst().orElseThrow();
		red.mousePressed(mouse(red,MouseEvent.MOUSE_PRESSED,red.getWidth()/4,0));
		red.mouseDragged(mouse(red,MouseEvent.MOUSE_DRAGGED,red.getWidth()*3/4,red.getWidth()/2));
		red.mouseReleased(mouse(red,MouseEvent.MOUSE_RELEASED,red.getWidth()*3/4,0));
		Color changed=picker.getColor(); assertTrue(changed.getRed()>100);
		picker.setColorModel(GLColorModel.OKLCH);
		assertTrue(history.undo()); assertEquals(Color.BLACK,picker.getColor()); assertFalse(history.canUndo());
		assertTrue(history.redo()); assertEquals(changed,picker.getColor());
	}
	@Test void outputModelIsIndependentAndEyedropperResourceLoads() {
		GLColorPicker picker=new GLColorPicker(new Color(84,120,181,97));
		picker.setColorModel(GLColorModel.OKLAB);
		picker.setOutputColorModel(GLColorModel.RGB);
		assertEquals(GLColorModel.OKLAB,picker.getColorModel());
		assertEquals(GLColorModel.RGB,picker.getOutputColorModel());
		assertTrue(picker.getColorExpression().startsWith("rgb("));
		assertNotNull(com.vingame.glswing.rendering.GLSvgDocument.loadResource(
				"com/vingame/glswing/icons/eyedropper.svg").rasterize(24,24));
	}
	@Test void expressionFormattingControlsNotationPrecisionAndTrailingZeros() {
		Color color=new Color(84,120,181,97);
		GLColorExpressionFormat normalized=new GLColorExpressionFormat(
				GLColorExpressionFormat.ChannelNotation.NORMALIZED_FLOAT,3,true);
		assertEquals("rgb(0.329f, 0.471f, 0.71f, 0.38f)",
				GLColorModel.RGB.format(GLColorModel.RGB.channels(color),97/255d,normalized));
		GLColorExpressionFormat bytes=normalized.withChannelNotation(
				GLColorExpressionFormat.ChannelNotation.BYTE);
		assertEquals("rgb(84, 120, 181, 97)",
				GLColorModel.RGB.format(GLColorModel.RGB.channels(color),97/255d,bytes));
		String hsv=GLColorModel.HSV.format(GLColorModel.HSV.channels(Color.GREEN),1,bytes);
		assertEquals("hsv(120deg, 255, 255, 255)",hsv);
		assertEquals(Color.GREEN,GLThemeValues.parseColor(hsv));
		GLColorExpressionFormat percent=new GLColorExpressionFormat(
				GLColorExpressionFormat.ChannelNotation.PERCENT,2,true);
		assertEquals("hsv(120deg, 100%, 100%, 100%)",
				GLColorModel.HSV.format(GLColorModel.HSV.channels(Color.GREEN),1,percent));
		GLColorExpressionFormat compactOpaque = bytes.withOmitOpaqueAlpha(true);
		assertEquals("hsv(120deg, 255, 255)",
				GLColorModel.HSV.format(GLColorModel.HSV.channels(Color.GREEN), 1,
						compactOpaque));
		assertEquals("oklab(255, 0f, 0f)", GLColorModel.OKLAB.format(
				new double[] { 1, 0, 0 }, 1, compactOpaque));
		assertThrows(IllegalArgumentException.class,()->new GLColorExpressionFormat(
				GLColorExpressionFormat.ChannelNotation.PERCENT,13,true));
	}
	@Test void themeAutoFormatUsesTheConfiguredColorPolicy() {
		GLCodeEditor editor=new GLCodeEditor(
				"palette.sample=rgb(0.500000f, 1.000000f, 0.000000f, 1.000000f)\n"
				+ "button.background=${palette.sample}");
		editor.setLanguage(GLCodeLanguages.GLTHEME);
		editor.setColorExpressionFormat(new GLColorExpressionFormat(
				GLColorExpressionFormat.ChannelNotation.NORMALIZED_FLOAT,3,true,true));
		editor.formatCode();
		assertEquals("palette.sample=rgb(0.502f, 1f, 0f)\n"
				+ "button.background=${palette.sample}",editor.getText());
	}
	@Test void validChannelTextUpdatesColorBeforeCommit() {
		GLColorPicker picker=new GLColorPicker(Color.BLACK);picker.setBounds(0,0,480,226);
		GLTextEditor red=descendants(picker).stream().filter(child ->
				child.getClass().getSimpleName().equals("ChannelEditor"))
				.map(GLTextEditor.class::cast).findFirst().orElseThrow();
		red.setText("128");
		assertEquals(128,picker.getColor().getRed());
		assertEquals(0,picker.getColor().getGreen());
	}
	@Test void groupedChannelEditorsMirrorIncompleteTextBeforeValidation() {
		GLColorPicker picker=new GLColorPicker(new Color(20,40,60));picker.setBounds(0,0,480,230);
		java.util.List<GLTextEditor> editors=descendants(picker).stream().filter(child ->
				child.getClass().getSimpleName().equals("ChannelEditor"))
				.map(GLTextEditor.class::cast).toList();
		assertEquals(4,editors.size());
		GLTextEditor red=editors.get(0);
		red.mousePressed(mouse(red,MouseEvent.MOUSE_PRESSED,12,14,0,0));
		red.mouseDragged(mouse(red,MouseEvent.MOUSE_DRAGGED,12,42,0,28));
		red.mouseReleased(mouse(red,MouseEvent.MOUSE_RELEASED,12,42,0,0));
		Color before=picker.getColor();red.setText("-");
		assertEquals("-",editors.get(0).getText());
		assertEquals("-",editors.get(1).getText());
		assertNotEquals("-",editors.get(2).getText());
		assertEquals(before,picker.getColor());
		assertFalse(red.commit());
		assertNotEquals("-",red.getText());
	}
	private static java.util.List<com.vingame.glswing.component.GLContainer> descendants(
			com.vingame.glswing.component.GLContainer root) {
		java.util.List<com.vingame.glswing.component.GLContainer> result=new java.util.ArrayList<>();
		for(var child:root.getGLChildren()) {result.add(child);result.addAll(descendants(child));}
		return result;
	}
	private static GLMouseEvent mouse(GLColorSlider target,int id) {
		return mouse(target,id,target.getWidth()/2,0);
	}
	private static GLMouseEvent mouse(GLColorSlider target,int id,float x,float deltaX) {
		return new GLMouseEvent(new MouseEvent(new JPanel(),id,0,0,(int)x,12,1,false,MouseEvent.BUTTON1),
				new GLPickResult(0,target,x,12,x,12,1,0,0,1,0,0),deltaX,0);
	}
	private static GLMouseEvent mouse(com.vingame.glswing.component.GLContainer target,int id,
			float x,float y,float deltaX,float deltaY) {
		return new GLMouseEvent(new MouseEvent(new JPanel(),id,0,0,(int)x,(int)y,1,false,MouseEvent.BUTTON1),
				new GLPickResult(0,target,x,y,x,y,1,0,0,1,0,0),deltaX,deltaY);
	}
}
