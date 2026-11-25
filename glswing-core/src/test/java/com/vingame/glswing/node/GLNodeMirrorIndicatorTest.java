package com.vingame.glswing.node;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.vingame.glswing.rendering.GLComponentImageCapture;

class GLNodeMirrorIndicatorTest {
    @Test @EnabledIfSystemProperty(named="glswing.test.gl",matches="true")
    void mirrorIconRemainsVisibleBesideGroupAndCollapseIndicators() {
        for(boolean group:new boolean[]{false,true})for(boolean hidden:new boolean[]{false,true}) {
            var node=new GLNode(" ");node.setGroupIndicatorVisible(group);node.setContentHidden(hidden);node.setSize(240,100);
            var before=GLComponentImageCapture.capture(node,240,100,480,200,true);
            node.setMirrored(true);var after=GLComponentImageCapture.capture(node,240,100,480,200,true);
            int changed=0;for(int y=4;y<Math.round(node.getGLStyle().titleHeight()*2)-2;y++)for(int x=4;x<200;x++)
                if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
            assertTrue(changed>30,"Mirror indicator missing: group="+group+" hidden="+hidden);
            assertEquals(" ",node.getTitle());assertEquals("",node.getTitleBadge());assertTrue(node.isMirrored());
        }
    }
}
