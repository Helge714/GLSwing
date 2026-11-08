package com.vingame.glswing.ui;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Escaped Markdown subset shared by offline handbook and node-reference export. */
public final class GLMarkdown {
    private static final Pattern ANCHOR=Pattern.compile("<a id=\"([a-z0-9-]+)\"></a>");
    private GLMarkdown(){}
    public static String render(String markdown,String language,Function<String,String> resolve){
        var ids=new HashSet<String>();var anchors=ANCHOR.matcher(markdown);while(anchors.find())ids.add(anchors.group(1));
        var lines=markdown.replace("\r\n","\n").split("\n",-1);
        var content=new StringBuilder();var navigation=new StringBuilder();String title="Document";
        for(int i=0;i<lines.length;){
            String line=lines[i];if(line.isBlank()){i++;continue;}
            if(line.startsWith("```")){
                content.append("<pre><code>");i++;
                while(i<lines.length&&!lines[i].startsWith("```"))content.append(escape(lines[i++])).append('\n');
                content.append("</code></pre>");i++;continue;
            }
            if(line.matches("#{1,6} .*")){
                int level=line.indexOf(' ');String text=line.substring(level+1);if(level==1)title=text;
                String base=Normalizer.normalize(text,Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
                String id=base;for(int n=2;!ids.add(id);n++)id=base+"-"+n;
                content.append("<h").append(level).append(" id=\"").append(id).append("\">").append(inline(text,resolve)).append("</h").append(level).append('>');
                if(level==2)navigation.append("<a href=\"#").append(id).append("\">").append(escape(text)).append("</a> ");
                i++;continue;
            }
            if(line.startsWith("|")){
                content.append("<div class=\"table-wrap\" tabindex=\"0\"><table>");boolean first=true;
                while(i<lines.length&&lines[i].startsWith("|")){
                    String row=lines[i++];if(row.matches("[| :\\-]+"))continue;
                    String tag=first?"th":"td";content.append("<tr>");
                    for(String cell:row.substring(1).replaceFirst("\\|\\s*$","").split("\\|",-1))content.append('<').append(tag).append('>').append(inline(cell.strip(),resolve)).append("</").append(tag).append('>');
                    content.append("</tr>");first=false;
                }
                content.append("</table></div>");continue;
            }
            boolean ordered=line.matches("\\d+\\. .*"),unordered=line.startsWith("- ")||line.startsWith("* ");
            if(ordered||unordered){
                String tag=ordered?"ol":"ul";content.append('<').append(tag).append('>');
                while(i<lines.length&&(ordered?lines[i].matches("\\d+\\. .*"):lines[i].startsWith("- ")||lines[i].startsWith("* "))){
                    String item=lines[i++].replaceFirst(ordered?"^\\d+\\. ":"^[-*] ","");
                    while(i<lines.length&&lines[i].startsWith("  "))item+=" "+lines[i++].strip();
                    content.append("<li>").append(inline(item,resolve)).append("</li>");
                }
                content.append("</").append(tag).append('>');continue;
            }
            var paragraph=new StringBuilder(line);i++;
            while(i<lines.length&&!lines[i].isBlank()&&!lines[i].matches("^(#{1,6} |\\||```|[-*] |\\d+\\. ).*"))paragraph.append(' ').append(lines[i++]);
            content.append("<p>").append(inline(paragraph.toString(),resolve)).append("</p>");
        }
        String css="body{font:18px/1.65 system-ui,sans-serif;color:#202a35;background:#fafbfc;max-width:1400px;margin:32px auto;padding:0 28px}h1,h2,h3{line-height:1.25}h2{margin-top:2em}a{color:#1558aa}nav{display:flex;flex-wrap:wrap;gap:8px 20px;font-size:15px;border-bottom:1px solid #ccd1d7;padding:12px 0}.table-wrap{overflow:auto;margin:20px 0}table{border-collapse:collapse;min-width:680px;width:100%;font-size:16px}th,td{border:1px solid #ccd1d7;padding:9px 12px;text-align:left;vertical-align:top}th{background:#edf2f7}pre{overflow:auto;background:#edf2f7;padding:16px}code{font-family:Consolas,monospace}@media print{body{font-size:11pt}.table-wrap{overflow:visible}table{font-size:9pt;min-width:0}nav{display:none}}";
        return "<!doctype html><html lang=\""+language+"\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"+escape(title)+"</title><style>"+css+"</style></head><body><nav>"+navigation+"</nav><main>"+content+"</main></body></html>";
    }
    public static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String inline(String text,Function<String,String> resolve){
        var tokens=new ArrayList<String>();
        Function<String,String> keep=value->{tokens.add(value);return "\u0000"+(tokens.size()-1)+"\u0000";};
        text=Pattern.compile("`([^`]+)`").matcher(text).replaceAll(m->java.util.regex.Matcher.quoteReplacement(keep.apply("<code>"+escape(m.group(1))+"</code>")));
        text=Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)").matcher(text).replaceAll(m->{
            String target=resolve.apply(m.group(2));return java.util.regex.Matcher.quoteReplacement(keep.apply(target==null?escape(m.group()):"<a href=\""+escape(target)+"\">"+escape(m.group(1))+"</a>"));
        });
        text=ANCHOR.matcher(text).replaceAll(m->java.util.regex.Matcher.quoteReplacement(keep.apply(m.group())));
        text=escape(text).replaceAll("\\*\\*([^*]+)\\*\\*","<strong>$1</strong>");
        return Pattern.compile("\u0000(\\d+)\u0000").matcher(text).replaceAll(m->java.util.regex.Matcher.quoteReplacement(tokens.get(Integer.parseInt(m.group(1)))));
    }
}
