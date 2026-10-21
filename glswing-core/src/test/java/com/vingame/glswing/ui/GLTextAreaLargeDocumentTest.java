package com.vingame.glswing.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

class GLTextAreaLargeDocumentTest {
    private static final class Area extends GLTextArea { int rows(){return visualRowCount();} }
    @Test void wrappingDoesNotCopyTheWholeDocumentForEachCharacter(){
        var bean=ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocation=(com.sun.management.ThreadMXBean)bean;
        assumeTrue(allocation.isThreadAllocatedMemorySupported());allocation.setThreadAllocatedMemoryEnabled(true);
        var area=new Area();area.setBounds(0,0,728,484);area.setLineWrap(true);
        area.setText("Warm up font layout.");area.rows();
        String license="Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files. ".repeat(100);
        long thread=Thread.currentThread().getId(),before=allocation.getThreadAllocatedBytes(thread);
        area.setText(license);int rows=area.rows();
        long bytes=allocation.getThreadAllocatedBytes(thread)-before;
        assertTrue(rows>1);assertEquals(license,area.getText());
        assertTrue(bytes<96L*1024*1024,"Wrapping allocated "+bytes+" bytes for "+license.length()+" characters");
    }
}
