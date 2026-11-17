package com.createengines.content.blueprint;

import java.util.function.Consumer;

/**
 * The way from the item to the drafting sheet, so that the item (which is common code and loads on a dedicated server)
 * never names the screen (which is client code). The client sets {@link #opener} when it starts; on a server it stays
 * a no-op and is never called, since the item only asks on the client side.
 */
public final class BlueprintViewer {

	/** Opens the drafting sheet for a recorded blueprint. Set by the client. */
	public static volatile Consumer<RecordedBlueprint> opener = recorded -> {
	};

	/** Copies a recorded blueprint's string to the clipboard. Set by the client. */
	public static volatile Consumer<RecordedBlueprint> copier = recorded -> {
	};

	/** Opens the dialog where a blueprint string is pasted. Set by the client. */
	public static volatile Runnable importer = () -> {
	};

	private BlueprintViewer() {
	}

	public static void open(RecordedBlueprint recorded) {
		opener.accept(recorded);
	}
}
