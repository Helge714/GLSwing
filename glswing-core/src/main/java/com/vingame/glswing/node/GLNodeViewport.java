package com.vingame.glswing.node;
/** Conservative world viewport: retain antialiasing, shadows and external sockets. */
record GLNodeViewport(float left,float top,float right,float bottom,float zoom){
    private static final ClassValue<Boolean> plainCoordinates=new ClassValue<>(){
        @Override protected Boolean computeValue(Class<?> type){
            for(var current=type;current!=null&&current!=com.vingame.glswing.component.GLContainer.class;current=current.getSuperclass()){
                try{current.getDeclaredMethod("transformComponent",com.vingame.glswing.rendering.GLContext2D.class);return false;}
                catch(NoSuchMethodException inherited){ }
            }
            return true;
        }
    };
    static GLNodeViewport of(float width,float height,float panX,float panY,float zoom){
        if(!(zoom>0))throw new IllegalArgumentException("Positive zoom required");
        float x=width*.5f,y=height*.5f,padding=2/zoom;
        return new GLNodeViewport(x+(-x-panX)/zoom-padding,y+(-y-panY)/zoom-padding,
            x+(width-x-panX)/zoom+padding,y+(height-y-panY)/zoom+padding,zoom);
    }
    boolean includes(GLNode node){
        if(!plainCoordinates.get(node.getClass()))return true; // Custom presentation transforms may move visible content.
        var overflow=node.getVisualOverflow();float scale=Math.min(1,zoom);
        return node.getX()-overflow.left()/scale<=right&&node.getX()+node.getWidth()+overflow.right()/scale>=left
            &&node.getY()-overflow.top()/scale<=bottom&&node.getY()+node.getHeight()+overflow.bottom()/scale>=top;
    }
}
