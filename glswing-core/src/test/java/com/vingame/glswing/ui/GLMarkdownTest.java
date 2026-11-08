package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GLMarkdownTest {
    @Test void plainPagesAndTablesRemainReadableWithoutATitle() throws Exception {
        var doc = GLMarkdownView.parse("A **plain** page.\n\n| A | B |\n| --- | --- |\n| 1 | 2 |\n", "en");
        assertTrue(doc.plain().contains("A plain page."));
        assertTrue(doc.plain().contains("1\t2"));
        assertEquals("Document", doc.title());
    }
    @Test void linksAnchorsAndCodeStayStructured() throws Exception {
        var doc = GLMarkdownView.parse("# Reference\n\n## Types\n\n[Value](types.md#value) and `x < y`.\n", "en");
        assertEquals("Reference", doc.title());
        assertTrue(doc.blocks().stream().filter(b -> b instanceof GLMarkdownView.Text)
            .map(b -> (GLMarkdownView.Text)b).flatMap(t -> t.spans.stream()).anyMatch(s -> "types.md#value".equals(s.link())));
        assertTrue(GLMarkdown.render("# X\n\n<script>alert(1)</script>", "en", x -> x).contains("&lt;script&gt;"));
    }
}
