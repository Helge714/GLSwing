package com.vingame.glswing.ui;

import java.text.BreakIterator;
import java.util.Locale;

/** Unicode word boundaries shared by plain single- and multi-line editors. */
final class GLTextSelectionRanges {
    record Range(int start,int end) { }
    private GLTextSelectionRanges() { }
    static Range word(String text,int offset) {
        if(text.isEmpty())return new Range(0,0);
        int probe=Math.max(0,Math.min(offset,text.length()-1));
        var words=BreakIterator.getWordInstance(Locale.getDefault());words.setText(text);
        int start=words.preceding(probe+1),end=words.following(probe);
        return new Range(start==BreakIterator.DONE?0:start,end==BreakIterator.DONE?text.length():end);
    }
}
