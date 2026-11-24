package com.vingame.glswing.node.composite;

import com.vingame.glswing.node.model.GLNodeId;

/** Routes one public composite property to an internal node property. */
public record GLCompositePropertyMapping(String publicPropertyId,
		GLNodeId internalNodeId, String internalPropertyId, java.util.Map<String,com.vingame.glswing.node.model.GLNodeValue> values) {
    public GLCompositePropertyMapping(String publicPropertyId,GLNodeId internalNodeId,String internalPropertyId){this(publicPropertyId,internalNodeId,internalPropertyId,java.util.Map.of());}
    public com.vingame.glswing.node.model.GLNodeValue mappedValue(com.vingame.glswing.node.model.GLNodeValue value){return values.isEmpty()?value:values.get(String.valueOf(value.toJavaValue()));}
	public GLCompositePropertyMapping {
        values=java.util.Map.copyOf(values);
		if (!valid(publicPropertyId) || internalNodeId == null
				|| !valid(internalPropertyId))
			throw new IllegalArgumentException("Invalid composite property mapping");
	}
	private static boolean valid(String value) {
		return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*");
	}
}
