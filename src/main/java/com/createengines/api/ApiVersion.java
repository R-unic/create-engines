package com.createengines.api;

/**
 * The version of the public API, which is its own number and not the mod's. It follows semantic versioning:
 * <ul>
 *     <li>a <b>major</b> change removes or alters something an addon could call or implement, and only comes with a new
 *     major version of the mod;</li>
 *     <li>a <b>minor</b> change adds something (a method, a type, an event), so code written for an older minor still
 *     compiles and runs;</li>
 *     <li>nothing else changes the surface.</li>
 * </ul>
 * The surface is {@code com.createengines.api}, plus {@link com.createengines.engine.sim.TorqueSource} and
 * {@link com.createengines.engine.sim.StatusLine}, which a provider returns. It is pinned in
 * {@code src/test/resources/compat/api-surface.txt} and checked by {@code ApiSurfaceTest}; see {@code docs/API.md}.
 */
public final class ApiVersion {

	// These are deliberately not compile-time constants. javac copies a constant's value into the class that reads it, so an
	// addon compiled against API 1.2 would carry "2" for ever and never see the version actually installed. Going through a
	// method keeps the read at run time, which is the only time an addon wants to know.
	public static final int MAJOR = of(1);
	public static final int MINOR = of(0);
	public static final int PATCH = of(0);

	/** {@code MAJOR.MINOR.PATCH}. */
	public static final String VERSION = MAJOR + "." + MINOR + "." + PATCH;

	private ApiVersion() {
	}

	private static int of(int value) {
		return value;
	}

	/**
	 * Whether the installed API is at least {@code major.minor}: the same major version and a minor version that is not
	 * older. An addon that can do without a feature added in a later minor version asks this before using it.
	 */
	public static boolean isAtLeast(int major, int minor) {
		return MAJOR == major && MINOR >= minor;
	}
}
