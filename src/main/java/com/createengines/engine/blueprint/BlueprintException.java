package com.createengines.engine.blueprint;

/**
 * Why a blueprint was refused. The {@link Refusal} is stable and meant for the player's message (each becomes a lang
 * key later); the detail names the offending part or value for the log.
 */
public final class BlueprintException extends Exception {

	private static final long serialVersionUID = 1L;

	/** The reasons a blueprint is turned away. Adding one is fine; renaming one changes a message key. */
	public enum Refusal {
		/** Not a blueprint string at all: no prefix, or nothing after it. */
		NOT_A_BLUEPRINT,
		/** Written by a later release of the mod than this one. */
		NEWER_FORMAT,
		/** Longer than the limits allow, as text, as a body once unpacked, or as a part count. */
		TOO_LARGE,
		/** The text is damaged: bad encoding, bad compression, cut short, or extra bytes on the end. */
		CORRUPT,
		/** The name or builder is too long or holds control characters. */
		BAD_NAME,
		/** A block id, property, value or dial name is empty, too long or has characters an id cannot have. */
		BAD_ID,
		/** A part is further from the origin than the span allows. */
		OUT_OF_RANGE,
		/** Two parts share a position. */
		DUPLICATE_POSITION,
		/** A block that does not belong to this mod's engines (the server's policy decides). */
		FOREIGN_BLOCK,
		/** A state property or value the block does not have. */
		UNKNOWN_STATE,
		/** A dial the block does not have, or a value outside its range. */
		BAD_DIAL,
		/** No parts. */
		EMPTY
	}

	private final Refusal refusal;

	public BlueprintException(Refusal refusal, String detail) {
		super(refusal + (detail == null || detail.isEmpty() ? "" : ": " + detail));
		this.refusal = refusal;
	}

	public Refusal refusal() {
		return refusal;
	}
}
