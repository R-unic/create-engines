package com.createengines.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * The mod's own render types, for geometry drawn in plain vertex colours. Extends {@link RenderType} only to reach its
 * protected state pieces.
 */
public final class CERenderTypes extends RenderType {

	/** Light: added to what is behind it, so overlapping layers brighten, and never hides anything. */
	public static final RenderType GAS_GLOW = create("create_engines_gas_glow", DefaultVertexFormat.POSITION_COLOR,
		VertexFormat.Mode.QUADS, 1536, false, false,
		CompositeState.builder()
			.setShaderState(POSITION_COLOR_SHADER)
			.setTransparencyState(LIGHTNING_TRANSPARENCY)
			.setCullState(NO_CULL)
			.setWriteMaskState(COLOR_WRITE)
			.createCompositeState(false));

	/** Haze: blended over what is behind it, tinting and dimming it. */
	public static final RenderType GAS_HAZE = create("create_engines_gas_haze", DefaultVertexFormat.POSITION_COLOR,
		VertexFormat.Mode.QUADS, 1536, false, false,
		CompositeState.builder()
			.setShaderState(POSITION_COLOR_SHADER)
			.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
			.setCullState(NO_CULL)
			.setWriteMaskState(COLOR_WRITE)
			.createCompositeState(false));

	private CERenderTypes() {
		super("", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 0, false, false, () -> {
		}, () -> {
		});
	}
}
