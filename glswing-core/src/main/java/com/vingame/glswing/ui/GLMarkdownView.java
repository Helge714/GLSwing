package com.vingame.glswing.ui;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import com.vingame.glswing.rendering.*;

/** Themed, retained Markdown view with tables, link picking and anchor navigation. */
public class GLMarkdownView extends GLControl {
    public record Span(String text,String link,boolean bold,boolean code){}
    public static final class Text {
        public final List<Span> spans=new ArrayList<>();public final Set<String> anchors=new LinkedHashSet<>();
        public int heading;public boolean pre,header;
        public String plain(){return spans.stream().map(Span::text).collect(java.util.stream.Collectors.joining());}
    }
    public static final class Table {public final List<List<Text>> rows=new ArrayList<>();}
    public record Document(String title,List<Object> blocks){
        public String plain(){var text=new StringBuilder();for(var b:blocks){if(b instanceof Text t)text.append(t.plain());else if(b instanceof Table table)for(var row:table.rows)text.append(row.stream().map(Text::plain).collect(java.util.stream.Collectors.joining("\t"))).append('\n');text.append("\n\n");}return text.toString();}
    }
    public static Document parse(String markdown,String language)throws IOException{
        String html=GLMarkdown.render(markdown,language,link->link);
        html=html.substring(html.indexOf("<main>")+6,html.lastIndexOf("</main>"));
        var blocks=new ArrayList<Object>();
        new ParserDelegator().parse(new StringReader(html),new HTMLEditorKit.ParserCallback(){
            Text text;Table table;List<Text> row;String link;int bold,code,ordered;boolean started=true;
            private void begin(int heading,boolean pre){text=new Text();text.heading=heading;text.pre=pre;blocks.add(text);}
            @Override public void handleStartTag(HTML.Tag tag,MutableAttributeSet attrs,int pos){
                String name=tag.toString();
                if(name.equals("h1"))started=true;if(!started)return;
                if(name.matches("h[1-6]")){begin(name.charAt(1)-'0',false);Object id=attrs.getAttribute(HTML.Attribute.ID);if(id!=null)text.anchors.add(id.toString());}
                else if(tag==HTML.Tag.TABLE){table=new Table();blocks.add(table);}
                else if(tag==HTML.Tag.TR){row=new ArrayList<>();table.rows.add(row);}
                else if(tag==HTML.Tag.TD||tag==HTML.Tag.TH){text=new Text();text.header=tag==HTML.Tag.TH;row.add(text);}
                else if(tag==HTML.Tag.P)begin(0,false);
                else if(tag==HTML.Tag.PRE)begin(0,true);
                else if(tag==HTML.Tag.OL)ordered=1;
                else if(tag==HTML.Tag.LI){begin(0,false);text.spans.add(new Span(ordered>0?(ordered++)+". ":"• ",null,false,false));}
                else if(tag==HTML.Tag.STRONG||tag==HTML.Tag.B)bold++;
                else if(tag==HTML.Tag.CODE)code++;
                else if(tag==HTML.Tag.A){Object href=attrs.getAttribute(HTML.Attribute.HREF);link=href==null?null:href.toString();Object id=attrs.getAttribute(HTML.Attribute.ID);if(id!=null&&text!=null)text.anchors.add(id.toString());}
            }
            @Override public void handleEndTag(HTML.Tag tag,int pos){
                if(tag==HTML.Tag.A)link=null;
                else if(tag==HTML.Tag.STRONG||tag==HTML.Tag.B)bold=Math.max(0,bold-1);
                else if(tag==HTML.Tag.CODE)code=Math.max(0,code-1);
                else if(tag==HTML.Tag.TABLE){table=null;text=null;}
                else if(tag==HTML.Tag.OL)ordered=0;
                else if(tag==HTML.Tag.P||tag==HTML.Tag.PRE||tag==HTML.Tag.LI||tag==HTML.Tag.TD||tag==HTML.Tag.TH||tag.toString().matches("h[1-6]"))text=null;
            }
            @Override public void handleText(char[] chars,int pos){if(started&&text!=null)text.spans.add(new Span(new String(chars),link,bold>0,code>0));}
        },true);
        String title=blocks.stream().filter(b->b instanceof Text t&&t.heading==1).map(b->((Text)b).plain()).findFirst().orElse("Document");
        return new Document(title,List.copyOf(blocks));
    }
    public record Run(String text,String link,GLFontSpec font,float x,float y,float width,float height){}
    public record Box(float x,float y,float width,float height,boolean header,boolean code){}
    private Document document=new Document("",List.of());
    private final List<Run> runs=new ArrayList<>();private final List<Box> boxes=new ArrayList<>();
    private final Map<String,Float> anchors=new HashMap<>();
    private final Consumer<String> follow;
    private float wrapWidth=900,extent;
    private com.vingame.glswing.input.GLPointerController pointer=com.vingame.glswing.input.GLPointerController.NONE;
    private boolean hand;
    public GLMarkdownView(Consumer<String> follow){this.follow=follow;}
    public Document document(){return document;}
    public void show(Document value){document=value;reflow(wrapWidth);}
    public float anchor(String id){return anchors.getOrDefault(id,0f);}
    public List<Run> runs(){return List.copyOf(runs);}
    public List<Box> boxes(){return List.copyOf(boxes);}
    private GLFontSpec font(Text text,Span span){
        return font(getGLTheme().labelStyle().font(),text,span);
    }
    private static GLFontSpec font(GLFontSpec base,Text text,Span span){
        float scale=text.heading==1?1.6f:text.heading==2?1.3f:text.heading>0?1.12f:1;
        return new GLFontSpec(span.code()||text.pre?GLFont.MONOSPACED:base.family(),text.heading>0||text.header||span.bold()?GLFont.BOLD:base.style(),base.size()*scale);
    }
    public static Set<GLFontSpec> fonts(Document document,GLFontSpec base){
        var result=new LinkedHashSet<GLFontSpec>();
        for(var block:document.blocks()){
            var texts=block instanceof Text t?List.of(t):((Table)block).rows.stream().flatMap(List::stream).toList();
            for(var text:texts)for(var span:text.spans)result.add(font(base,text,span));
        }
        return Set.copyOf(result);
    }
    public void reflow(float width){
        clearCursor();
        wrapWidth=Math.max(240,width);runs.clear();boxes.clear();anchors.clear();extent=wrapWidth;
        float y=18;
        for(var block:document.blocks()){
            if(block instanceof Text text){
                if(text.heading>0)y+=12;
                float height=layout(text,18,y,wrapWidth-36);if(text.pre)boxes.add(new Box(10,y-5,Math.max(wrapWidth-20,extent-20),height+10,false,true));
                y+=height+(text.heading>0?14:12);
            }else if(block instanceof Table table&&!table.rows.isEmpty()){
                int columns=table.rows.stream().mapToInt(List::size).max().orElse(1);float[] widths=new float[columns];Arrays.fill(widths,90);
                for(var row:table.rows)for(int col=0;col<row.size();col++)for(var span:row.get(col).spans){
                    var f=font(row.get(col),span).measurementFont();for(String word:span.text().split("\\s+"))widths[col]=Math.max(widths[col],Math.min(220,f.measureText(word)+24));
                }
                float sum=0;for(float w:widths)sum+=w;
                if(sum<wrapWidth-36){float extra=(wrapWidth-36-sum)/columns;for(int col=0;col<columns;col++)widths[col]+=extra;sum=wrapWidth-36;}
                extent=Math.max(extent,sum+36);
                for(var row:table.rows){
                    float x=18,height=0;for(int col=0;col<row.size();col++){height=Math.max(height,layout(row.get(col),x+10,y+8,widths[col]-20));x+=widths[col];}
                    height+=16;x=18;
                    for(int col=0;col<columns;col++){boxes.add(new Box(x,y,widths[col],height,!row.isEmpty()&&row.get(0).header,false));x+=widths[col];}
                    y+=height;
                }
                y+=18;
            }
        }
        setPreferredGLSize(extent,y+18);invalidateRecording();repaintGL();
    }
    private float layout(Text text,float x,float y,float width){
        for(String anchor:text.anchors)anchors.put(anchor,y);
        float px=x,py=y,line=0,total=0;
        for(var span:text.spans){
            var spec=font(text,span);var f=spec.measurementFont();float height=f.getLineHeight()*1.28f;line=Math.max(line,height);
            var pieces=Pattern.compile(text.pre?"[^\\n]+|\\n":"\\S+\\s*|\\s+").matcher(span.text());
            while(pieces.find()){
                String part=pieces.group();
                if(part.equals("\n")){py+=line;px=x;line=height;continue;}
                if(!text.pre&&px>x&&px+f.measureText(part.stripTrailing())>x+width){py+=line;px=x;line=height;}
                if(!text.pre&&px==x)part=part.stripLeading();
                // Oversized words wrap by code point rather than bleeding into neighboring cells.
                while(!text.pre&&f.measureText(part)>width&&!part.isEmpty()){
                    int cut=0;for(int end=part.offsetByCodePoints(0,1);end<=part.length();){if(f.measureText(part.substring(0,end))>width&&cut>0)break;cut=end;if(end==part.length())break;end=part.offsetByCodePoints(end,1);}
                    String fragment=part.substring(0,cut);float w=f.measureText(fragment);runs.add(new Run(fragment,span.link(),spec,px,py,w,height));
                    part=part.substring(cut);py+=line;px=x;line=height;
                }
                if(!part.isEmpty()){float w=f.measureText(part);runs.add(new Run(part,span.link(),spec,px,py,w,height));px+=w;extent=Math.max(extent,px+18);}
            }
            total=Math.max(total,py-y+line);
        }
        return Math.max(total,getGLTheme().labelStyle().font().measurementFont().getLineHeight()*1.28f);
    }
    public String linkAt(float x,float y){for(var run:runs)if(run.link()!=null&&x>=run.x()&&x<=run.x()+run.width()&&y>=run.y()&&y<=run.y()+run.height())return run.link();return null;}
    @Override protected void controlMouseClicked(com.vingame.glswing.input.GLMouseEvent e){if(e.getButton()==1){String link=linkAt(e.getLocalX(),e.getLocalY());if(link!=null){follow.accept(link);e.consume();}}}
    private void updateCursor(com.vingame.glswing.input.GLMouseEvent e){
        var next=e.getPointerController();
        if(pointer!=next){clearCursor();pointer=next;}
        boolean linked=linkAt(e.getLocalX(),e.getLocalY())!=null;
        if(linked&&!hand){pointer.setCursor(this,com.vingame.glswing.input.GLPointerCursor.HAND);hand=true;}
        else if(!linked&&hand)clearCursor();
    }
    private void clearCursor(){if(hand)pointer.clearCursor(this);hand=false;pointer=com.vingame.glswing.input.GLPointerController.NONE;}
    @Override protected void controlMouseEntered(com.vingame.glswing.input.GLMouseEvent e){updateCursor(e);}
    @Override protected void controlMouseMoved(com.vingame.glswing.input.GLMouseEvent e){updateCursor(e);}
    @Override protected void controlMouseExited(com.vingame.glswing.input.GLMouseEvent e){clearCursor();}
    @Override protected void detached(com.vingame.glswing.component.GLParent parent){clearCursor();super.detached(parent);}
    @Override protected void themeChanged(){super.themeChanged();if(document!=null)reflow(wrapWidth);}
    @Override protected void record(GLContext2D c){
        var theme=getGLTheme();c.fillRect(0,0,getWidth(),getHeight(),theme.textEditorStyle().backgroundColor());
        for(var box:boxes){
            if(box.header()||box.code())c.fillRect(box.x(),box.y(),box.width(),box.height(),theme.sliderStyle().background());
            if(!box.code()){c.drawLine(box.x(),box.y(),box.x()+box.width(),box.y(),theme.sliderStyle().borderColor());c.drawLine(box.x(),box.y(),box.x(),box.y()+box.height(),theme.sliderStyle().borderColor());c.drawLine(box.x()+box.width(),box.y(),box.x()+box.width(),box.y()+box.height(),theme.sliderStyle().borderColor());c.drawLine(box.x(),box.y()+box.height(),box.x()+box.width(),box.y()+box.height(),theme.sliderStyle().borderColor());}
        }
        for(var run:runs){
            var font=getGLFont(run.font());var color=run.link()==null?theme.labelStyle().textColor():theme.sliderStyle().progressLightColor();float baseline=run.y()+font.getAscent();
            c.drawUIText(font,run.text(),run.x(),baseline,color);
            if(run.link()!=null)c.drawLine(run.x(),baseline+2,run.x()+run.width(),baseline+2,color);
        }
    }
}
