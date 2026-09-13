package com.vingame.glswing.node;

/** Visual row order used by node port rows. */
public enum GLNodePortOrder {
	/** Output rows precede input rows. */
	OUTPUTS_FIRST,
	/** Input rows precede output rows. */
	INPUTS_FIRST,
	/** Rows retain the order stored in the composite contract. */
	DECLARATION_ORDER
}
