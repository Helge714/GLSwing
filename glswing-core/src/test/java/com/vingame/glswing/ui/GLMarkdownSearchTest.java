package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GLMarkdownSearchTest {
    static class View extends GLMarkdownView {
        View(){super(link->{});}
        String tooltip(){return toolTipTextAt(null);}
    }
    static class Editor extends GLTextArea {
        void f3(boolean previous){controlKeyPressed(new com.vingame.glswing.input.GLKeyEvent(new java.awt.event.KeyEvent(new java.awt.Canvas(),java.awt.event.KeyEvent.KEY_PRESSED,0,previous?java.awt.event.InputEvent.SHIFT_DOWN_MASK:0,java.awt.event.KeyEvent.VK_F3,java.awt.event.KeyEvent.CHAR_UNDEFINED)));}
    }
    @Test void textAndCodeBaseShareF3Navigation(){var editor=new Editor();editor.setText("one one");editor.startSearch("one",GLTextSearchOptions.DEFAULT);editor.f3(false);assertEquals(1,editor.getActiveSearchMatchIndex());editor.f3(true);assertEquals(0,editor.getActiveSearchMatchIndex());}
    @Test void textAndMarkdownUseIdenticalMatchingOptionsAndWrapNavigation()throws Exception {
        var page=new View();page.show(GLMarkdownView.parse("# Test\n\nHello **world** and hello world.\n", "en"));
        var editor=new GLTextArea();editor.setText(page.document().plain());
        for(var options:List.of(GLTextSearchOptions.DEFAULT,new GLTextSearchOptions(true,false,false),new GLTextSearchOptions(false,true,false),new GLTextSearchOptions(false,false,true))){
            editor.select(0,0);
            String query=options.regularExpression()?"hello\\s+world":"hello world";
            assertEquals(editor.startSearch(query,options),page.startSearch(query,options));
            assertEquals(editor.getSelectedText(),page.getSelectedText());
            assertEquals(editor.findNext(query,options),page.findNext(query,options));
            assertEquals(editor.getSelectedText(),page.getSelectedText());
        }
        assertEquals(2,page.startSearch("hello world",GLTextSearchOptions.DEFAULT));
        assertTrue(page.searchHighlights().size()>=4,"Matches cross inline style boundaries");
        assertEquals(0,page.getActiveSearchMatchIndex());page.findNext(false);assertEquals(1,page.getActiveSearchMatchIndex());page.findNext(false);assertEquals(0,page.getActiveSearchMatchIndex());page.findNext(true);assertEquals(1,page.getActiveSearchMatchIndex());
        page.startSearch("[",new GLTextSearchOptions(false,false,true));assertNotNull(page.getSearchPatternError());assertTrue(page.searchHighlights().isEmpty());
        page.clearSearch();assertNull(page.getSearchPatternError());assertEquals(-1,page.getActiveSearchMatchIndex());
    }
    @Test void matchesSpanWrappedWordsCodeAndTableCellsAndSurviveReflow()throws Exception {
        var page=new View();page.show(GLMarkdownView.parse("# Test\n\nA longwordthatiswrappedacrossmultiplelinesandcontainsneedlehere near the end.\n\n```\ncode needle\n```\n\n| A | B |\n| --- | --- |\n| alpha | beta |\n", "en"));
        page.reflow(240);assertEquals(2,page.startSearch("needle",GLTextSearchOptions.DEFAULT));
        var positions=new ArrayList<GLMarkdownView.SearchHighlight>();page.setSearchMatchListener(positions::add);page.findNext(false);assertFalse(positions.isEmpty());
        page.reflow(700);assertEquals(1,page.getActiveSearchMatchIndex());assertFalse(page.searchHighlights().isEmpty());
        assertEquals(1,page.startSearch("alpha\tbeta",GLTextSearchOptions.DEFAULT));assertEquals(2,page.searchHighlights().size());
        page.show(GLMarkdownView.parse("# New\n\nNothing here.\n","en"));assertEquals(0,page.getSearchMatchCount());assertTrue(page.searchHighlights().isEmpty());
    }
    @Test void tooltipsOnlyShowExplicitHelp(){var page=new View();assertNull(page.tooltip());page.setToolTipText("Useful help");assertEquals("Useful help",page.tooltip());}
}
