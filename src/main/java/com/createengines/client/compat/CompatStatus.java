package com.createengines.client.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What each recipe viewer plugin registered, so the client audit can tell a plugin that ran from one that was never
 * found. Plain Java on purpose: the audit may read it with no viewer installed, and it names no viewer class.
 */
public final class CompatStatus {

	private static final Map<String, Integer> REFINING_ROUTES = new ConcurrentHashMap<>();

	private static final Map<String, Integer> FUEL_PAGES = new ConcurrentHashMap<>();
	private static final Map<String, Integer> PART_PAGES = new ConcurrentHashMap<>();

	private CompatStatus() {
	}

	/** Called by a plugin after it hands the viewer its refining routes. */
	public static void refiningRoutesRegistered(String viewer, int routes) {
		REFINING_ROUTES.put(viewer, routes);
	}

	/** How many routes {@code viewer} was given, or -1 if its plugin has not run (yet). */
	public static int refiningRoutes(String viewer) {
		return REFINING_ROUTES.getOrDefault(viewer, -1);
	}

	/** Called by a plugin after it hands the viewer its fuel and part pages. */
	public static void pagesRegistered(String viewer, int fuels, int parts) {
		FUEL_PAGES.put(viewer, fuels);
		PART_PAGES.put(viewer, parts);
	}

	/** How many fuel pages {@code viewer} was given, or -1 if its plugin has not run (yet). */
	public static int fuelPages(String viewer) {
		return FUEL_PAGES.getOrDefault(viewer, -1);
	}

	/** How many part pages {@code viewer} was given, or -1 if its plugin has not run (yet). */
	public static int partPages(String viewer) {
		return PART_PAGES.getOrDefault(viewer, -1);
	}
}
