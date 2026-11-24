package com.vingame.glswing.node;

/** Visual row order used by node port rows. */
public enum GLNodePortOrder {
	/** Output rows come first; all other rows retain their joint declaration order. */
	OUTPUTS_FIRST,
	/** Input rows precede output rows. */
	INPUTS_FIRST,
	/** Rows retain the order stored in the composite contract. */
	DECLARATION_ORDER
}
