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

import net.minecraft.client.Minecraft;
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
 *
 * <p>This runs for every cylinder in view every frame, so it is written not to allocate: the shape is worked out once
 * into scratch arrays (shared by the haze and the glow), the angles come from tables, and {@link ChamberLod} cuts it
 * finer or coarser, or not at all, by distance. The render thread is the only caller, so the scratch is shared.
 */
public final class ChamberVolume {

	/** How far inside the bore's wall the gas stops, so it never z-fights with it. */
	private static final float INSET = 1f;
	/** How far the cross-section is pulled from a circle towards the bore's square; 1 would reach the frame posts. */
	private static final float SQUARENESS = 0.85f;

	private static final Tier[] TIERS = {new Tier(ChamberLod.Level.DETAILED), new Tier(ChamberLod.Level.COARSE)};
	private static final Frame[] FRAMES = new Frame[6];
	static {
		for (Direction bore : Direction.values())
			FRAMES[bore.ordinal()] = new Frame(bore);
	}

	// Scratch for one cylinder at a time, sized for the finest level
	private static final int MAX_POINTS = (ChamberLod.Level.DETAILED.rings + 1) * ChamberLod.Level.DETAILED.sides;
	private static final float[] SHELL = new float[MAX_POINTS * 3];
	private static final float[] SHADE = new float[MAX_POINTS];
	private static final float[] CORE = new float[(ChamberLod.Level.DETAILED.bands + 1) * ChamberLod.Level.DETAILED.sides * 3];
	private static final float[] RING_COLOUR = new float[(ChamberLod.Level.DETAILED.rings + 1) * 3];
	private static final float[] MIDDLE = new float[6];
	private static final float[] BASE = new float[3];
	private static final float[] TIP = new float[3];
	private static final float[] ACROSS = new float[3];
	private static final float[] OUTWARD = new float[3];
	private static final Look LOOK = new Look();

	private ChamberVolume() {
	}

	public static void render(CylinderBlockEntity be, float renderTime, PoseStack ms, MultiBufferSource buffer) {
		Matrix4f pose = ms.last()
			.pose();
		ChamberLod.Level level = levelFor(be, pose);
		if (level == null)
			return;
		CylinderBlockEntity.Chamber chamber = be.chamber(renderTime);
		if (chamber == null)
			return;
		Look look = Look.of(chamber);
		if (look == null)
			return;
		boolean haze = look.hazeAlpha > 0.01f;
		boolean glow = look.glowAlpha > 0.01f;
		if (!haze && !glow)
			return;

		Tier tier = TIERS[level.ordinal()];
		Frame frame = FRAMES[be.getBlockState()
			.getValue(CylinderBlock.FACING)
			.ordinal()];
		float floor = (float) chamber.crown();
		float roof = (float) chamber.roof();
		float radius = (float) chamber.radius() * INSET;
		float time = renderTime * 0.3f;

		buildShell(tier, frame, floor, roof, radius, time, look.phase, look.turbulence);
		if (haze)
			emitShell(buffer.getBuffer(CERenderTypes.GAS_HAZE), pose, tier, look.hazeAlpha, look.hazeBottom, look.hazeTop);
		if (glow) {
			VertexConsumer vc = buffer.getBuffer(CERenderTypes.GAS_GLOW);
			emitShell(vc, pose, tier, look.glowAlpha, look.glowBottom, look.glowTop);
			if (look.core > 0.02f)
				core(vc, pose, tier, frame, floor, roof, radius, time, look);
			if (look.tongues > 0.02f)
				tongues(vc, pose, tier, frame, floor, roof, radius, time, look);
		}
	}

	/**
	 * How finely to draw this cylinder, or null to skip it. Only the world's own cylinders are judged by distance: a Ponder
	 * scene is drawn in its own space, where the pose says nothing about how far off the viewer is.
	 */
	private static ChamberLod.Level levelFor(CylinderBlockEntity be, Matrix4f pose) {
		if (be.getLevel() != Minecraft.getInstance().level)
			return ChamberLod.Level.DETAILED;
		float x = pose.m30();
		float y = pose.m31();
		float z = pose.m32();
		return ChamberLod.at(x * x + y * y + z * z);
	}

	/** The gas filling the bore: a wobbling tube from the crown to the head, positions and shading kept in scratch. */
	private static void buildShell(Tier tier, Frame frame, float floor, float roof, float radius, float time,
								   float phase, float turbulence) {
		int sides = tier.sides;
		frame.at(floor, 0, 0, MIDDLE, 0);
		frame.at(roof, 0, 0, MIDDLE, 3);
		for (int k = 0; k <= tier.rings; k++) {
			float h = tier.ringH[k];
			float u = Mth.lerp(h, floor, roof);
			// Gas pulled in a little at the ends, where it meets the piston and the head
			float waist = tier.waist[k];
			for (int s = 0; s < sides; s++) {
				float angle = tier.angle[s];
				float n = wobble(angle, h, time + phase);
				// Nearly fills the bore, rippling in and out against its walls but never through them
				float r = radius * waist * Math.min(1, 0.985f + 0.03f * n * turbulence);
				// The bore is square like the piston, so the cross-section is a rounded square, not a circle
				r *= tier.square[s];
				int i = k * sides + s;
				frame.at(u, tier.cos[s] * r, tier.sin[s] * r, SHELL, i * 3);
				SHADE[i] = 0.5f + 0.5f * wobble(angle * 2 + 1, h * 1.7f, time * 1.4f + 3);
			}
		}
	}

	/** Draws the shell built by {@link #buildShell}, then the crown's and the head's faces so the gas is seen to lie on them. */
	private static void emitShell(VertexConsumer vc, Matrix4f pose, Tier tier, float alpha, Vector3f bottom,
								  Vector3f top) {
		int sides = tier.sides;
		int rings = tier.rings;
		for (int k = 0; k <= rings; k++) {
			float h = tier.ringH[k];
			RING_COLOUR[k * 3] = Mth.lerp(h, bottom.x, top.x);
			RING_COLOUR[k * 3 + 1] = Mth.lerp(h, bottom.y, top.y);
			RING_COLOUR[k * 3 + 2] = Mth.lerp(h, bottom.z, top.z);
		}
		for (int k = 0; k < rings; k++)
			for (int s = 0; s < sides; s++) {
				int t = (s + 1) % sides;
				put(vc, pose, k * sides + s, k, alpha);
				put(vc, pose, k * sides + t, k, alpha);
				put(vc, pose, (k + 1) * sides + t, k + 1, alpha);
				put(vc, pose, (k + 1) * sides + s, k + 1, alpha);
			}
		for (int cap = 0; cap < 2; cap++) {
			int k = cap == 0 ? 0 : rings;
			int ring = k * sides;
			float mx = MIDDLE[cap * 3], my = MIDDLE[cap * 3 + 1], mz = MIDDLE[cap * 3 + 2];
			for (int s = 0; s < sides; s++) {
				int t = (s + 1) % sides;
				middle(vc, pose, mx, my, mz, k, alpha * 1.3f);
				put(vc, pose, ring + s, k, alpha);
				put(vc, pose, ring + t, k, alpha);
				middle(vc, pose, mx, my, mz, k, alpha * 1.3f);
			}
		}
	}

	/**
	 * The flame front: a squashed blob that starts at the plug and swells outward and down as the burn spreads, clipped to
	 * the space between crown and head so it flattens against them rather than passing through.
	 */
	private static void core(VertexConsumer vc, Matrix4f pose, Tier tier, Frame frame, float floor, float roof,
							 float radius, float time, Look look) {
		float span = roof - floor;
		float centre = Mth.lerp(0.35f + 0.65f * (1 - look.core), floor, roof);
		float reach = Math.max(0.03f, span * (0.2f + 0.65f * look.core));
		float width = radius * (0.35f + 0.6f * look.core);
		int sides = tier.sides;
		int bands = tier.bands;
		for (int p = 0; p <= bands; p++)
			for (int s = 0; s < sides; s++) {
				float azimuth = tier.angle[s];
				float bulge = 1 + 0.22f * wobble(azimuth, tier.polar[p] * 0.7f, time * 1.6f + look.phase);
				float u = Mth.clamp(centre + tier.polarCos[p] * reach * bulge, floor, roof);
				float r = tier.polarSin[p] * width * bulge;
				frame.at(u, tier.cos[s] * r, tier.sin[s] * r, CORE, (p * sides + s) * 3);
			}
		for (int k = 0; k < bands; k++)
			for (int s = 0; s < sides; s++) {
				int t = (s + 1) % sides;
				coreVertex(vc, pose, tier, k, s, look);
				coreVertex(vc, pose, tier, k, t, look);
				coreVertex(vc, pose, tier, k + 1, t, look);
				coreVertex(vc, pose, tier, k + 1, s, look);
			}
	}

	private static void coreVertex(VertexConsumer vc, Matrix4f pose, Tier tier, int band, int side, Look look) {
		int i = (band * tier.sides + side) * 3;
		// Brightest in the middle, shading to the flame's own colour at the rim
		float rim = tier.rim[band];
		vc.addVertex(pose, CORE[i], CORE[i + 1], CORE[i + 2])
			.setColor(Mth.lerp(rim, look.coreColor.x, look.glowBottom.x), Mth.lerp(rim, look.coreColor.y, look.glowBottom.y),
				Mth.lerp(rim, look.coreColor.z, look.glowBottom.z), look.coreAlpha * (1 - 0.6f * rim));
	}

	/** Tongues of flame hanging from the plug into the gas: jagged, each a different length, flickering. */
	private static void tongues(VertexConsumer vc, Matrix4f pose, Tier tier, Frame frame, float floor, float roof,
								float radius, float time, Look look) {
		float span = roof - floor;
		int count = tier.tongues;
		for (int i = 0; i < count; i++) {
			float angle = Mth.TWO_PI * (i + 0.37f * Mth.sin(i * 7.3f)) / count;
			float flicker = 0.5f + 0.5f * Mth.sin(time * 6 + i * 2.1f);
			float length = span * look.tongues * (0.35f + 0.55f * flicker);
			float tip = Math.max(floor, roof - length);
			float baseR = radius * 0.45f;
			float tipR = radius * (0.2f + 0.25f * Mth.sin(time * 3 + i));
			float half = 0.035f + 0.03f * flicker;
			float ca = Mth.cos(angle);
			float sa = Mth.sin(angle);
			frame.at(roof, ca * baseR, sa * baseR, BASE, 0);
			frame.at(tip, Mth.cos(angle + 0.5f) * tipR, Mth.sin(angle + 0.5f) * tipR, TIP, 0);
			// Two crossed fins, one across the way round the bore and one along its radius
			frame.at(roof, ca * baseR - sa * half, sa * baseR + ca * half, ACROSS, 0);
			frame.at(roof, ca * (baseR + half), sa * (baseR + half), OUTWARD, 0);
			for (int c = 0; c < 3; c++) {
				ACROSS[c] -= BASE[c];
				OUTWARD[c] -= BASE[c];
			}
			fin(vc, pose, BASE, ACROSS, TIP, look);
			fin(vc, pose, BASE, OUTWARD, TIP, look);
		}
	}

	private static void fin(VertexConsumer vc, Matrix4f pose, float[] base, float[] side, float[] tip, Look look) {
		Vector3f white = look.coreColor;
		Vector3f edge = look.glowBottom;
		vc.addVertex(pose, base[0] + side[0], base[1] + side[1], base[2] + side[2])
			.setColor(white.x, white.y, white.z, look.coreAlpha * 0.8f);
		vc.addVertex(pose, base[0] - side[0], base[1] - side[1], base[2] - side[2])
			.setColor(white.x, white.y, white.z, look.coreAlpha * 0.8f);
		vc.addVertex(pose, tip[0], tip[1], tip[2])
			.setColor(edge.x, edge.y, edge.z, 0);
		vc.addVertex(pose, tip[0], tip[1], tip[2])
			.setColor(edge.x, edge.y, edge.z, 0);
	}

	/** One shell vertex: the scratch point {@code point}, coloured for ring {@code ring} and its shade. */
	private static void put(VertexConsumer vc, Matrix4f pose, int point, int ring, float alpha) {
		float shade = SHADE[point];
		float light = 0.78f + 0.22f * shade;
		vc.addVertex(pose, SHELL[point * 3], SHELL[point * 3 + 1], SHELL[point * 3 + 2])
			.setColor(RING_COLOUR[ring * 3] * light, RING_COLOUR[ring * 3 + 1] * light, RING_COLOUR[ring * 3 + 2] * light,
				alpha * (0.6f + 0.4f * shade));
	}

	/** A face's middle vertex, full shade. */
	private static void middle(VertexConsumer vc, Matrix4f pose, float x, float y, float z, int ring, float alpha) {
		vc.addVertex(pose, x, y, z)
			.setColor(RING_COLOUR[ring * 3], RING_COLOUR[ring * 3 + 1], RING_COLOUR[ring * 3 + 2], alpha);
	}

	/** Smooth pseudo-noise from sums of sines, about -1 to 1, that moves with {@code time}. */
	private static float wobble(float angle, float h, float time) {
		return 0.5f * Mth.sin(3 * angle + time * 2 + h * 5) + 0.3f * Mth.sin(5 * angle - time * 3 + h * 9 + 1.7f)
			+ 0.2f * Mth.sin(2 * angle + h * 13 + time * 4.1f);
	}

	/** What a level's shape needs that does not change from frame to frame: its angles and the shell's fixed profile. */
	private static final class Tier {
		final int sides, rings, bands, tongues;
		/** Round the bore: the angle of each side and its cosine, sine and how far the rounded square reaches. */
		final float[] angle, cos, sin, square;
		/** Along the bore: each ring's fraction of the way and how far the gas is pulled in there. */
		final float[] ringH, waist;
		/** Over the flame front: each band edge's polar angle, its cosine and sine, and how far out to the rim it is. */
		final float[] polar, polarCos, polarSin, rim;

		Tier(ChamberLod.Level level) {
			sides = level.sides;
			rings = level.rings;
			bands = level.bands;
			tongues = level.tongues;
			angle = new float[sides];
			cos = new float[sides];
			sin = new float[sides];
			square = new float[sides];
			for (int s = 0; s < sides; s++) {
				angle[s] = Mth.TWO_PI * s / sides;
				cos[s] = Mth.cos(angle[s]);
				sin[s] = Mth.sin(angle[s]);
				square[s] = Mth.lerp(SQUARENESS, 1, 1 / Math.max(Math.abs(cos[s]), Math.abs(sin[s])));
			}
			ringH = new float[rings + 1];
			waist = new float[rings + 1];
			for (int k = 0; k <= rings; k++) {
				ringH[k] = (float) k / rings;
				waist[k] = 0.98f + 0.02f * Mth.sin(ringH[k] * Mth.PI);
			}
			polar = new float[bands + 1];
			polarCos = new float[bands + 1];
			polarSin = new float[bands + 1];
			rim = new float[bands + 1];
			for (int p = 0; p <= bands; p++) {
				polar[p] = Mth.PI * p / bands;
				polarCos[p] = Mth.cos(polar[p]);
				polarSin[p] = Mth.sin(polar[p]);
				rim[p] = (float) Math.pow(polarSin[p], 1.5);
			}
		}
	}

	/** The bore's own axes: along it from the cylinder's middle towards the head, and two across it. */
	private static final class Frame {
		private final float ax, ay, az, e1x, e1y, e1z, e2x, e2y, e2z;

		Frame(Direction bore) {
			Vector3f along = new Vector3f(bore.step());
			Vector3f helper = Math.abs(along.y) > 0.9f ? new Vector3f(1, 0, 0)
				: new Vector3f(0, 1, 0);
			Vector3f e1 = new Vector3f(along).cross(helper)
				.normalize();
			Vector3f e2 = new Vector3f(along).cross(e1);
			ax = along.x;
			ay = along.y;
			az = along.z;
			e1x = e1.x;
			e1y = e1.y;
			e1z = e1.z;
			e2x = e2.x;
			e2y = e2.y;
			e2z = e2.z;
		}

		/** Writes into {@code out} at {@code at} the point {@code u} along the bore from the block's centre and {@code x}, {@code y} across it. */
		void at(float u, float x, float y, float[] out, int at) {
			out[at] = 0.5f + u * ax + x * e1x + y * e2x;
			out[at + 1] = 0.5f + u * ay + x * e1y + y * e2y;
			out[at + 2] = 0.5f + u * az + x * e1z + y * e2z;
		}
	}

	/** What the gas looks like at this point in the cycle. One instance is reused for every cylinder. */
	private static final class Look {
		float hazeAlpha, glowAlpha, core, coreAlpha, tongues, turbulence, phase;
		final Vector3f hazeBottom = new Vector3f(), hazeTop = new Vector3f(),
			glowBottom = new Vector3f(), glowTop = new Vector3f(), coreColor = new Vector3f();

		private void reset() {
			hazeAlpha = glowAlpha = core = coreAlpha = tongues = phase = 0;
			turbulence = 1;
			hazeBottom.set(0, 0, 0);
			hazeTop.set(0, 0, 0);
			glowBottom.set(0, 0, 0);
			glowTop.set(0, 0, 0);
			coreColor.set(1, 1, 1);
		}

		static Look of(CylinderBlockEntity.Chamber chamber) {
			CylinderStatus status = chamber.status();
			double cycle = chamber.cycle();
			CycleResult result = status.result();
			boolean fired = result == CycleResult.FIRED;
			int faults = status.faults();
			Look look = LOOK;
			look.reset();
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
