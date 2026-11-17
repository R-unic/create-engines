package com.createengines.engine.blueprint;

/**
 * What the server will accept in a blueprint, asked part by part. The format cannot know which blocks exist or what a
 * dial's range is, so the game side supplies this and {@link BlueprintRules#check} applies it to every part before
 * anything is built.
 */
public interface BlueprintPolicy {

	/** Whether this block may appear at all. Engines and what they need, never an arbitrary block. */
	boolean allowsBlock(String block);

	/** Whether the block has this property and the property takes this value. */
	boolean allowsState(String block, String property, String value);

	/** Smallest value of the dial on that block, or {@link Integer#MAX_VALUE} when the block has no such dial. */
	int dialMin(String block, String dial);

	/** Largest value of the dial on that block, or {@link Integer#MIN_VALUE} when the block has no such dial. */
	int dialMax(String block, String dial);
}
