package com.vingame.glswing.ui;

/** Search surface shared by editable text, code editors and read-only documents. */
public interface GLTextSearchTarget {
    String getSelectedText();
    int startSearch(String query, GLTextSearchOptions options);
    boolean findNext(String query, GLTextSearchOptions options);
    boolean findPrevious(String query, GLTextSearchOptions options);
    /** Navigate the existing query; true selects the previous match. */
    boolean findNext(boolean previous);
    int getSearchMatchCount();
    int getActiveSearchMatchIndex();
    String getSearchPatternError();
    void clearSearch();
    default boolean isEditable(){return false;}
    default boolean replaceCurrent(String query,String replacement,GLTextSearchOptions options){return false;}
    default int replaceAll(String query,String replacement,GLTextSearchOptions options){return 0;}
}
