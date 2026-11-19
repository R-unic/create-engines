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

	public static final int MAJOR = 1;
	public static final int MINOR = 0;
	public static final int PATCH = 0;

	/** {@code MAJOR.MINOR.PATCH}. */
	public static final String VERSION = MAJOR + "." + MINOR + "." + PATCH;

	private ApiVersion() {
	}
}
