package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.content.AnimationDiagnostic;
import com.createengines.content.StructureCulling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The animation diagnostic's report and the in-view test it explains, driven directly because the diagnostic is switched off
 * in an ordinary run. The diagnostic follows one cylinder through shared state, so its test has a batch of its own.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class AnimationDiagnosticGameTests {

	private static final long SECOND = 1_000_000_000L;

	@GameTest(template = "empty", batch = "animationDiagnostic")
	public static void theDiagnosticReportsOncePerSecondForTheCylinderItFollows(GameTestHelper helper) {
		AnimationDiagnostic.reset();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		BlockPos other = pos.east(3);
		Object pose = new Object();
		Vec3 camera = Vec3.atCenterOf(pos)
			.add(0, 0, -10);
		Vector3f ahead = new Vector3f(0, 0, 1);
		Vector3f aside = new Vector3f(1, 0, 0);
		// The client hands the camera over once a frame; switched off, a frame is not even counted
		AnimationDiagnostic.camera(camera, ahead);
		AnimationDiagnostic.frame(helper.getLevel(), pos, pos, true, true, 1f, 100, 90, pose, null);

		String first = AnimationDiagnostic.record(0, helper.getLevel(), pos, pos, true, true, 1f, 100, 90, pose, null, camera, ahead);
		if (first == null || !first.contains("frames=1 skippedOutOfView=0 posed=1 poseChanged=1 ")
			|| !first.contains("distance=10.0 offAxisDegrees=0.0"))
			helper.fail("The first frame of the followed cylinder should report at once, one frame posed and changed, ten blocks from the camera and dead ahead: " + first);
		if (AnimationDiagnostic.record(SECOND / 2, helper.getLevel(), pos, pos, true, false, 1f, 100, 90, pose, pose, camera, ahead) != null)
			helper.fail("A second frame half a second later should only be counted");
		if (AnimationDiagnostic.record(SECOND / 2, helper.getLevel(), other, other, true, true, 1f, 100, 90, pose, null, camera, ahead) != null)
			helper.fail("Another cylinder is not the one followed and should not be counted or reported");
		String second = AnimationDiagnostic.record(SECOND + SECOND / 5, helper.getLevel(), pos, pos, false, true, 1f, 100, 90, pose, pose,
			camera, aside);
		if (second == null || !second.contains("frames=2 skippedOutOfView=1 posed=1 poseChanged=0 ") || !second.contains("offAxisDegrees=90.0"))
			helper.fail("A second on, the report should say two frames, one skipped, one posed and unchanged, and a part a quarter turn off the view: " + second);
		AnimationDiagnostic.reset();
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aPartIsInViewWhenItIsInFrontOfTheCameraWhateverTheRenderOrigin(GameTestHelper helper) {
		Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, 10, 0, 1, 0);
		Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 1.6f, 0.05f, 256f);
		FrustumIntersection frustum = new FrustumIntersection(new Matrix4f(projection).mul(view));
		Vec3i origin = new Vec3i(1000, 0, 1000);
		BlockPos ground = helper.absolutePos(BlockPos.ZERO);

		if (!StructureCulling.inView(new Vec3(0, 0, 10), Vec3i.ZERO, frustum))
			helper.fail("A part ahead of the camera should be in view");
		if (StructureCulling.inView(new Vec3(0, 0, -10), Vec3i.ZERO, frustum))
			helper.fail("A part behind the camera should not be in view");
		if (!StructureCulling.inView(new Vec3(1000, 0, 1010), origin, frustum))
			helper.fail("A part ahead of the camera should be in view when its position and the origin are both far from zero");
		if (StructureCulling.inView(new Vec3(1000, 0, 1010), Vec3i.ZERO, frustum))
			helper.fail("The same position against the wrong origin is nowhere near the camera, which is the bug this test exists for");
		// Off a moving structure Flywheel's own answer stands, whatever the frustum says
		if (!StructureCulling.visible(helper.getLevel(), ground, origin, frustum, true) || StructureCulling.visible(helper.getLevel(), ground, origin, frustum, false)
			|| !StructureCulling.visible(helper.getLevel(), ground, frustum, true) || StructureCulling.visible(helper.getLevel(), ground, frustum, false))
			helper.fail("Off a structure, Flywheel's own answer must stand");
		helper.succeed();
	}
}
