package com.createengines.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.effects.ExhaustLook;
import com.createengines.engine.CylinderStatus;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.CylinderFault;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * The gas in a cylinder as a body rather than a scatter of dots, drawn between the piston's crown and the head and
 * following both as they move: a shell that fills the bore and wobbles against its walls, a core that is the flame
 * front, and tongues of flame licking down from the plug. What it looks like is the stroke: cool haze going in, charge
 * warming as it is squeezed, a fireball that burns out as the piston is driven down, smoke swept out.
 *
 * <p>Drawn from the same shape twice, once added to the scene (what glows) and once blended (what shades), in vertex
 * colours with no texture.
 */
public final class ChamberVolume {

	private static final int SIDES = 12;
	private static final int RINGS = 7;
	private static final int BANDS = 6;
	private static final int TONGUES = 5;
	/** How far inside the bore's wall the gas stops, so it never z-fights with it. */
	private static final float INSET = 1f;
	/** How far the cross-section is pulled from a circle towards the bore's square; 1 would reach the frame posts. */
	private static final float SQUARENESS = 0.85f;

	private ChamberVolume() {
	}

	public static void render(CylinderBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer) {
		CylinderBlockEntity.Chamber chamber = be.chamber(renderTime);
		if (chamber == null)
			return;
		Look look = Look.of(chamber);
		if (look == null)
			return;

		Frame frame = new Frame(be.getBlockState()
			.getValue(CylinderBlock.FACING));
		Shape shape = new Shape(frame, (float) chamber.crown(), (float) chamber.roof(),
			(float) chamber.radius() * INSET, renderTime * 0.3f, look);
		Matrix4f pose = ms.last()
			.pose();
		if (look.hazeAlpha > 0.01f)
			shell(buffer.getBuffer(CERenderTypes.GAS_HAZE), pose, shape, look.hazeAlpha, look.hazeBottom, look.hazeTop);
		if (look.glowAlpha > 0.01f) {
			VertexConsumer glow = buffer.getBuffer(CERenderTypes.GAS_GLOW);
			shell(glow, pose, shape, look.glowAlpha, look.glowBottom, look.glowTop);
			if (look.core > 0.02f)
				core(glow, pose, shape);
			if (look.tongues > 0.02f)
				tongues(glow, pose, shape);
		}
	}

	/** The gas filling the bore: a wobbling tube from the crown to the head, coloured along its length. */
	private static void shell(VertexConsumer vc, Matrix4f pose, Shape shape, float alpha, Vector3f bottom, Vector3f top) {
		Vector3f[][] points = new Vector3f[RINGS + 1][SIDES];
		float[][] shade = new float[RINGS + 1][SIDES];
		for (int k = 0; k <= RINGS; k++) {
			float h = (float) k / RINGS;
			float u = Mth.lerp(h, shape.floor, shape.roof);
			// Gas pulled in a little at the ends, where it meets the piston and the head
			float waist = 0.98f + 0.02f * Mth.sin(h * Mth.PI);
			for (int s = 0; s < SIDES; s++) {
				float angle = Mth.TWO_PI * s / SIDES;
				float n = wobble(angle, h, shape.time + shape.look.phase);
				// Nearly fills the bore, rippling in and out against its walls but never through them
				float r = shape.radius * waist * Math.min(1, 0.985f + 0.03f * n * shape.look.turbulence);
				// The bore is square like the piston, so the cross-section is a rounded square, not a circle
				float c = Mth.cos(angle);
				float sn = Mth.sin(angle);
				r *= Mth.lerp(SQUARENESS, 1, 1 / Math.max(Math.abs(c), Math.abs(sn)));
				points[k][s] = shape.frame.at(u, c * r, sn * r);
				shade[k][s] = 0.5f + 0.5f * wobble(angle * 2 + 1, h * 1.7f, shape.time * 1.4f + 3);
			}
		}
		for (int k = 0; k < RINGS; k++)
			for (int s = 0; s < SIDES; s++) {
				int t = (s + 1) % SIDES;
				float h0 = (float) k / RINGS;
				float h1 = (float) (k + 1) / RINGS;
				put(vc, pose, points[k][s], bottom, top, h0, alpha, shade[k][s]);
				put(vc, pose, points[k][t], bottom, top, h0, alpha, shade[k][t]);
				put(vc, pose, points[k + 1][t], bottom, top, h1, alpha, shade[k + 1][t]);
				put(vc, pose, points[k + 1][s], bottom, top, h1, alpha, shade[k + 1][s]);
			}
		// The crown and the head's face, so the gas is seen to lie on them
		for (int cap = 0; cap < 2; cap++) {
			int k = cap == 0 ? 0 : RINGS;
			Vector3f middle = shape.frame.at(cap == 0 ? shape.floor : shape.roof, 0, 0);
			for (int s = 0; s < SIDES; s++) {
				int t = (s + 1) % SIDES;
				put(vc, pose, middle, bottom, top, cap, alpha * 1.3f, 1);
				put(vc, pose, points[k][s], bottom, top, cap, alpha, shade[k][s]);
				put(vc, pose, points[k][t], bottom, top, cap, alpha, shade[k][t]);
				put(vc, pose, middle, bottom, top, cap, alpha * 1.3f, 1);
			}
		}
	}

	/**
	 * The flame front: a squashed blob that starts at the plug and swells outward and down as the burn spreads, clipped to
	 * the space between crown and head so it flattens against them rather than passing through.
	 */
	private static void core(VertexConsumer vc, Matrix4f pose, Shape shape) {
		Look look = shape.look;
		float span = shape.roof - shape.floor;
		float centre = Mth.lerp(0.35f + 0.65f * (1 - look.core), shape.floor, shape.roof);
		float reach = Math.max(0.03f, span * (0.2f + 0.65f * look.core));
		float width = shape.radius * (0.35f + 0.6f * look.core);
		for (int k = 0; k < BANDS; k++) {
			float a0 = Mth.PI * k / BANDS;
			float a1 = Mth.PI * (k + 1) / BANDS;
			for (int s = 0; s < SIDES; s++) {
				float s0 = Mth.TWO_PI * s / SIDES;
				float s1 = Mth.TWO_PI * (s + 1) / SIDES;
				coreVertex(vc, pose, shape, a0, s0, centre, reach, width);
				coreVertex(vc, pose, shape, a0, s1, centre, reach, width);
				coreVertex(vc, pose, shape, a1, s1, centre, reach, width);
				coreVertex(vc, pose, shape, a1, s0, centre, reach, width);
			}
		}
	}

	private static void coreVertex(VertexConsumer vc, Matrix4f pose, Shape shape, float polar, float azimuth,
								   float centre, float reach, float width) {
		Look look = shape.look;
		float bulge = 1 + 0.22f * wobble(azimuth, polar * 0.7f, shape.time * 1.6f + look.phase);
		float u = Mth.clamp(centre + Mth.cos(polar) * reach * bulge, shape.floor, shape.roof);
		float r = Mth.sin(polar) * width * bulge;
		Vector3f at = shape.frame.at(u, Mth.cos(azimuth) * r, Mth.sin(azimuth) * r);
		// Brightest in the middle, shading to the flame's own colour at the rim
		float rim = (float) Math.pow(Mth.sin(polar), 1.5);
		Vector3f colour = new Vector3f(look.coreColor).lerp(look.glowBottom, rim);
		vc.addVertex(pose, at.x, at.y, at.z)
			.setColor(colour.x, colour.y, colour.z, look.coreAlpha * (1 - 0.6f * rim));
	}

	/** Tongues of flame hanging from the plug into the gas: jagged, each a different length, flickering. */
	private static void tongues(VertexConsumer vc, Matrix4f pose, Shape shape) {
		Look look = shape.look;
		float span = shape.roof - shape.floor;
		for (int i = 0; i < TONGUES; i++) {
			float angle = Mth.TWO_PI * (i + 0.37f * Mth.sin(i * 7.3f)) / TONGUES;
			float flicker = 0.5f + 0.5f * Mth.sin(shape.time * 6 + i * 2.1f);
			float length = span * look.tongues * (0.35f + 0.55f * flicker);
			float tip = Math.max(shape.floor, shape.roof - length);
			float baseR = shape.radius * 0.45f;
			float tipR = shape.radius * (0.2f + 0.25f * Mth.sin(shape.time * 3 + i));
			float half = 0.035f + 0.03f * flicker;
			Vector3f baseAt = shape.frame.at(shape.roof, Mth.cos(angle) * baseR, Mth.sin(angle) * baseR);
			Vector3f tipAt = shape.frame.at(tip, Mth.cos(angle + 0.5f) * tipR, Mth.sin(angle + 0.5f) * tipR);
			// Two crossed fins, one across the way round the bore and one along its radius
			Vector3f round = shape.frame.at(shape.roof, Mth.cos(angle) * baseR - Mth.sin(angle) * half,
				Mth.sin(angle) * baseR + Mth.cos(angle) * half)
				.sub(baseAt);
			Vector3f out = shape.frame.at(shape.roof, Mth.cos(angle) * (baseR + half), Mth.sin(angle) * (baseR + half))
				.sub(baseAt);
			fin(vc, pose, baseAt, round, tipAt, look);
			fin(vc, pose, baseAt, out, tipAt, look);
		}
	}

	private static void fin(VertexConsumer vc, Matrix4f pose, Vector3f base, Vector3f side, Vector3f tip, Look look) {
		Vector3f a = new Vector3f(base).add(side);
		Vector3f b = new Vector3f(base).sub(side);
		Vector3f white = look.coreColor;
		Vector3f edge = look.glowBottom;
		vc.addVertex(pose, a.x, a.y, a.z)
			.setColor(white.x, white.y, white.z, look.coreAlpha * 0.8f);
		vc.addVertex(pose, b.x, b.y, b.z)
			.setColor(white.x, white.y, white.z, look.coreAlpha * 0.8f);
		vc.addVertex(pose, tip.x, tip.y, tip.z)
			.setColor(edge.x, edge.y, edge.z, 0);
		vc.addVertex(pose, tip.x, tip.y, tip.z)
			.setColor(edge.x, edge.y, edge.z, 0);
	}

	private static void put(VertexConsumer vc, Matrix4f pose, Vector3f at, Vector3f bottom, Vector3f top, float h,
							float alpha, float shade) {
		float light = 0.78f + 0.22f * shade;
		vc.addVertex(pose, at.x, at.y, at.z)
			.setColor(Mth.lerp(h, bottom.x, top.x) * light, Mth.lerp(h, bottom.y, top.y) * light,
				Mth.lerp(h, bottom.z, top.z) * light, alpha * (0.6f + 0.4f * shade));
	}

	/** Smooth pseudo-noise from sums of sines, about -1 to 1, that moves with {@code time}. */
	private static float wobble(float angle, float h, float time) {
		return 0.5f * Mth.sin(3 * angle + time * 2 + h * 5) + 0.3f * Mth.sin(5 * angle - time * 3 + h * 9 + 1.7f)
			+ 0.2f * Mth.sin(2 * angle + h * 13 + time * 4.1f);
	}

	/** The bore's own axes: along it from the cylinder's middle towards the head, and two across it. */
	private static final class Frame {
		private final Vector3f along;
		private final Vector3f e1;
		private final Vector3f e2;

		Frame(Direction bore) {
			along = new Vector3f(bore.step());
			Vector3f helper = Math.abs(along.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
			e1 = new Vector3f(along).cross(helper)
				.normalize();
			e2 = new Vector3f(along).cross(e1);
		}

		/** The point {@code u} along the bore from the block's centre and {@code x}, {@code y} across it. */
		Vector3f at(float u, float x, float y) {
			return new Vector3f(0.5f).fma(u, along)
				.fma(x, e1)
				.fma(y, e2);
		}
	}

	/** Where the gas is: the bore's frame, the crown and head along it, its radius, and the animation clock. */
	private record Shape(Frame frame, float floor, float roof, float radius, float time, Look look) {
	}

	/** What the gas looks like at this point in the cycle. */
	private static final class Look {
		float hazeAlpha, glowAlpha, core, coreAlpha, tongues, turbulence = 1, phase;
		final Vector3f hazeBottom = new Vector3f(), hazeTop = new Vector3f(), glowBottom = new Vector3f(),
			glowTop = new Vector3f(), coreColor = new Vector3f(1, 1, 1);

		static Look of(CylinderBlockEntity.Chamber chamber) {
			CylinderStatus status = chamber.status();
			double cycle = chamber.cycle();
			CycleResult result = status.result();
			boolean fired = result == CycleResult.FIRED;
			int faults = status.faults();
			Look look = new Look();
			look.phase = (float) (chamber.pos() % 97);

			if (cycle < 180) {
				if (CylinderFault.intakeShut(faults))
					return null;
				// Cool air filling the space the piston leaves
				float fill = (float) cycle / 180;
				look.hazeAlpha = 0.1f + 0.14f * fill;
				look.hazeBottom.set(0.6f, 0.78f, 1f);
				look.hazeTop.set(0.85f, 0.94f, 1f);
				look.turbulence = 1.6f;
			} else if (cycle < 360) {
				// The charge squeezed: blue to orange, and starting to glow
				float squeeze = (float) (cycle - 180) / 180;
				look.hazeAlpha = 0.18f;
				look.hazeBottom.set(0.5f, 0.66f, 0.95f);
				look.hazeTop.set(0.7f, 0.82f, 1f);
				look.glowAlpha = 0.04f + 0.3f * squeeze * squeeze;
				look.glowBottom.set(Mth.lerp(squeeze, 0.5f, 1f), Mth.lerp(squeeze, 0.7f, 0.42f), Mth.lerp(squeeze, 1f, 0.12f));
				look.glowTop.set(Mth.lerp(squeeze, 0.7f, 1f), Mth.lerp(squeeze, 0.82f, 0.62f), Mth.lerp(squeeze, 1f, 0.25f));
				look.turbulence = 1.2f;
			} else if (cycle < 540) {
				float burn = (float) (cycle - 360) / 180;
				if (fired) {
					float early = Math.min(1, burn / 0.5f);
					float late = Math.max(0, (burn - 0.5f) / 0.5f);
					look.hazeAlpha = 0.2f * (1 - late);
					look.hazeBottom.set(0.45f, 0.14f, 0.05f);
					look.hazeTop.set(0.6f, 0.25f, 0.1f);
					look.glowAlpha = 0.55f * (1 - late * late) + 0.05f;
					look.glowBottom.set(1f, Mth.lerp(burn, 0.45f, 0.18f), Mth.lerp(burn, 0.1f, 0.03f));
					look.glowTop.set(1f, Mth.lerp(burn, 0.85f, 0.4f), Mth.lerp(burn, 0.45f, 0.1f));
					look.core = early;
					look.coreAlpha = 0.85f * (1 - burn * 0.85f);
					look.coreColor.set(1f, Mth.lerp(burn, 0.97f, 0.7f), Mth.lerp(burn, 0.8f, 0.3f));
					look.tongues = Math.max(0, 1 - burn * 2.2f);
					look.turbulence = 2.2f;
				} else if (ExhaustLook.unlit(result)) {
					look.hazeAlpha = 0.24f;
					look.hazeBottom.set(0.42f, 0.42f, 0.42f);
					look.hazeTop.set(0.6f, 0.6f, 0.6f);
				} else
					return null;
			} else {
				if (CylinderFault.exhaustShut(faults))
					return null;
				if (!fired && !ExhaustLook.unlit(result))
					return null;
				float out = (float) (cycle - 540) / 180;
				float dark = status.rich() ? 0.1f : fired ? 0.3f : 0.55f;
				look.hazeAlpha = 0.32f * (1 - 0.5f * out);
				look.hazeBottom.set(dark, dark, dark);
				look.hazeTop.set(dark + 0.1f, dark + 0.1f, dark + 0.1f);
				look.turbulence = 1.8f;
				if (fired) {
					look.glowAlpha = 0.2f * (1 - out);
					look.glowBottom.set(0.9f, 0.3f, 0.06f);
					look.glowTop.set(0.5f, 0.12f, 0.03f);
				}
			}
			return look;
		}
	}
}
