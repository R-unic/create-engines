package com.createengines.config;

import com.createengines.engine.sim.Tuning;

/**
 * The rules of keeping a connected player's wear mode the same as the server's, apart from the packet that carries it
 * ({@link WearModeSync}). NeoForge sends the server's config only while a player joins, so without this a switch made
 * afterwards leaves their goggles, tooltips and wrecked parts showing the old setting until they join again.
 */
public final class WearModeLink {

	/** What the connected players were last told, or null before they have been told anything. */
	private Boolean told;

	/** Whether the players have to be told, the mode now being {@code on}; if so they count as told. */
	public boolean due(boolean on) {
		if (told != null && told == on)
			return false;
		told = on;
		return true;
	}

	/** What the players were last told, or null. */
	public Boolean told() {
		return told;
	}

	/**
	 * Takes the server's word for the mode. A game hosting its own server shares {@link Tuning} with it, where a message
	 * still on its way could undo a later switch, so there it is ignored.
	 */
	public static void receive(boolean on, boolean ownServer) {
		if (!ownServer)
			Tuning.wearMode = on;
	}
}
