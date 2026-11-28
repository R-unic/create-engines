package com.createengines.gametest;

import static com.createengines.gametest.BlueprintRigs.*;

import com.createengines.CreateEngines;
import com.createengines.content.blueprint.BlueprintApplier;
import com.createengines.content.blueprint.BlueprintBuilder;
import com.createengines.content.blueprint.BlueprintRecorder;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Turned copies of a recorded build scan and apply alike. */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintTurnGameTests {

	/** Where the four turned copies go, far enough apart that none touches another. */
	private static final BlockPos[] TURN_SITES = {new BlockPos(12, 2, 12), new BlockPos(34, 2, 12), new BlockPos(12, 2, 34),
		new BlockPos(34, 2, 34)};

	private static double angleGap(double a, double b) {
		return Math.abs(((a - b) % 360 + 540) % 360 - 180);
	}

	/**
	 * An engine built from a blueprint turned any of the four ways, with the mirror a turn sometimes needs, scans as the same
	 * engine: the same cylinders at the turned places, each with the same top-dead-centre angle and cam timing and no fault.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		recordedTestEngine(helper);
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** The twin-rotor engine, whose second rotor sits half a turn round: its throw angles are the sharpest test of the turn. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedRotaryBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.buildRotary(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSecondRotor(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		turnedCopiesScanAlike(helper, TestEngine.HOUSING);
	}

	/** Two crank lines geared together, the second turning the same way: one engine on two parallel axes. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedCoupledBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addCoupledLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** Two cylinders on one throw, one standing up and one leaning out to the north: the facing of a cylinder is turned too. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedVEngineScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addSideCylinder(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		var layout = com.createengines.engine.topology.EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.cylinders().size() != 2)
			helper.fail("the V engine should scan as two cylinders, not " + (layout == null ? "nothing" : layout.cylinders().size()));
		if (angleGap(layout.cylinders().get(0).tdcAngle(), layout.cylinders().get(1).tdcAngle()) < 1)
			helper.fail("the two cylinders should not share a top dead centre");
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** The intake runs through a bent pipe (two bends, one going up) to an opening: every turn and mirror must keep it reaching the head. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedBentPipeNetworkScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addBentIntake(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		var layout = com.createengines.engine.topology.EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.ports().stream().noneMatch(p -> p.kind() == com.createengines.content.airflow.PortKind.INTAKE
			&& p.openings() == 1 && p.heads() == 1))
			helper.fail("the bent intake should reach the head through one opening: " + (layout == null ? "no engine" : layout.ports()));
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** The second line turns the other way (four cogwheels, not three): its sense must survive every turn and mirror. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aTurnedCounterRotatingBuildScansAsTheSameEngine(GameTestHelper helper) throws BlueprintException {
		TestEngine.build(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), true);
		TestEngine.addCounterRotatingLine(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		var layout = com.createengines.engine.topology.EngineScanner.scan(helper.getLevel(), helper.absolutePos(TestEngine.PRIMARY));
		if (layout == null || layout.cylinders().size() != 2)
			helper.fail("the counter-rotating engine should scan as two cylinders, not "
				+ (layout == null ? "nothing" : layout.cylinders().size()));
		if (layout.cylinders().get(0).sense() == layout.cylinders().get(1).sense())
			helper.fail("the two lines should turn opposite ways");
		turnedCopiesScanAlike(helper, TestEngine.HEAD);
	}

	/** A pipe's open ends turn and mirror with the build: a pipe cannot be left pointing the way it did before the turn. */
	@GameTest(template = "empty")
	public static void aTurnedPipeKeepsItsOpenEndsPointingTheSameWay(GameTestHelper helper) {
		String pipe = "create_engines:intake_manifold";
		Blueprint blueprint = new Blueprint("Pipes", "", java.util.List.of(
			new Blueprint.Part(0, 0, 0, pipe, java.util.Map.of("north", "true", "up", "true"), java.util.Map.of()),
			new Blueprint.Part(0, 0, -1, pipe, java.util.Map.of("south", "true", "east", "true"), java.util.Map.of())));
		// A quarter turn clockwise: north goes to east, east to south, south to west
		Blueprint turned = com.createengines.content.blueprint.BlueprintTurner.turn(blueprint,
			net.minecraft.world.level.block.Rotation.CLOCKWISE_90, net.minecraft.world.level.block.Mirror.NONE);
		Blueprint.Part a = partAt(turned, 0, 0, 0), b = partAt(turned, 1, 0, 0);
		if (a == null || b == null)
			helper.fail("the turned pipes are not where a quarter turn puts them");
		else if (!a.state().equals(java.util.Map.of("east", "true", "up", "true"))
			|| !b.state().equals(java.util.Map.of("west", "true", "south", "true")))
			helper.fail("the turned pipes' open ends are " + a.state() + " and " + b.state());
		// Mirrored left to right (z flips): north and south swap, east and west stay
		Blueprint mirrored = com.createengines.content.blueprint.BlueprintTurner.turn(blueprint,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
		Blueprint.Part c = partAt(mirrored, 0, 0, 0), d = partAt(mirrored, 0, 0, 1);
		if (c == null || d == null)
			helper.fail("the mirrored pipes are not where a mirror puts them");
		else if (!c.state().equals(java.util.Map.of("south", "true", "up", "true"))
			|| !d.state().equals(java.util.Map.of("north", "true", "east", "true")))
			helper.fail("the mirrored pipes' open ends are " + c.state() + " and " + d.state());
		helper.succeed();
	}

	private static Blueprint.Part partAt(Blueprint blueprint, int x, int y, int z) {
		for (Blueprint.Part part : blueprint.parts())
			if (part.x() == x && part.y() == y && part.z() == z)
				return part;
		return null;
	}

	private static void turnedCopiesScanAlike(GameTestHelper helper, BlockPos click) throws BlueprintException {
		ServerLevel level = helper.getLevel();
		BlueprintRecorder.Result result = BlueprintRecorder.record(level, helper.absolutePos(click), "Turn", "t");
		if (!result.ok())
			helper.fail("recording failed: " + result.failure());
		Blueprint plain = result.blueprint();
		RecordedBlueprint recorded = new RecordedBlueprint(BlueprintCodec.encode(plain), "Turn", plain.parts()
			.size());
		BlockPos primary = result.origin();
		var original = com.createengines.engine.topology.EngineScanner.scan(level, primary);
		if (original == null || original.incomplete())
			helper.fail("the original engine did not scan");
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		for (int k = 0; k < 4; k++) {
			net.minecraft.world.level.block.Rotation rotation = com.createengines.content.blueprint.BlueprintTurner.TURNS.get(k);
			net.minecraft.world.level.block.Mirror mirror = com.createengines.content.blueprint.BlueprintTurner.mirrorFor(plain, rotation);
			BlockPos site = helper.absolutePos(TURN_SITES[k]);
			BlueprintBuilder.Result built = BlueprintBuilder.build(player, level, site, recorded, rotation);
			if (built.status() != BlueprintBuilder.Status.BUILT)
				helper.fail(rotation + " (" + mirror + "): building gave " + built);
			var layout = com.createengines.engine.topology.EngineScanner.scan(level, site);
			if (layout == null || layout.incomplete() || layout.broken())
				helper.fail(rotation + ": the turned engine did not scan whole");
			if (layout.cylinders().size() != original.cylinders().size())
				helper.fail(rotation + ": " + layout.cylinders().size() + " cylinders, expected " + original.cylinders().size());
			int flip = 0; // sense is relative to the first crank line; a turn can change which line that is, and then every sense and top dead centre changes sign together
			for (var cylinder : original.cylinders()) {
				BlockPos rel = cylinder.pos().subtract(primary);
				int[] at = com.createengines.content.blueprint.BlueprintTurner.place(rel.getX(), rel.getZ(), rotation, mirror);
				BlockPos expected = site.offset(at[0], rel.getY(), at[1]);
				var twin = layout.cylinders().stream().filter(c -> c.pos().equals(expected)).findFirst().orElse(null);
				if (twin == null)
					helper.fail(rotation + " (" + mirror + "): no cylinder at the turned place " + expected);
				int relative = twin.sense() * cylinder.sense();
				if (flip == 0)
					flip = relative;
				if (relative != flip)
					helper.fail(rotation + " (" + mirror + "): the senses did not all change together, at " + rel);
				if (angleGap(twin.tdcAngle(), flip * cylinder.tdcAngle()) > 1e-3)
					helper.fail(rotation + " (" + mirror + "): top dead centre " + twin.tdcAngle() + ", expected " + flip * cylinder.tdcAngle()
						+ " (sense " + twin.sense() + ", was " + cylinder.sense() + ", at " + rel + ")");
				if (angleGap(twin.lobeAngle(), cylinder.lobeAngle()) > 1e-3)
					helper.fail(rotation + " (" + mirror + "): cam timing " + twin.lobeAngle() + ", expected " + cylinder.lobeAngle());
				if (twin.faults() != cylinder.faults() || twin.sparkPlug() != cylinder.sparkPlug())
					helper.fail(rotation + " (" + mirror + "): faults " + twin.faults() + " expected " + cylinder.faults());
			}
			// The pipe networks reach the same openings, turned: same kinds, counts, heads and opening places
			if (layout.ports().size() != original.ports().size())
				helper.fail(rotation + " (" + mirror + "): " + layout.ports().size() + " pipe networks, expected " + original.ports().size());
			for (var port : original.ports()) {
				// Which network is which can change with the turn, so find the one holding the first opening's turned place
				var twinPort = (com.createengines.engine.topology.EngineLayout.PortNetwork) null;
				for (BlockPos outlet : port.outlets()) {
					BlockPos rel = outlet.subtract(primary);
					int[] at = com.createengines.content.blueprint.BlueprintTurner.place(rel.getX(), rel.getZ(), rotation, mirror);
					BlockPos expected = site.offset(at[0], rel.getY(), at[1]);
					if (twinPort == null)
						twinPort = layout.ports().stream().filter(p -> p.kind() == port.kind() && p.outlets().contains(expected)).findFirst()
							.orElse(null);
					if (twinPort == null || !twinPort.outlets().contains(expected))
						helper.fail(rotation + " (" + mirror + "): the " + port.kind() + " opening at " + rel + " is not at " + expected);
				}
				if (twinPort != null && (twinPort.openings() != port.openings() || twinPort.heads() != port.heads()))
					helper.fail(rotation + " (" + mirror + "): the " + port.kind() + " network has " + twinPort.openings() + " openings and "
						+ twinPort.heads() + " heads, expected " + port.openings() + " and " + port.heads());
			}
		}
		helper.succeed();
	}

	/** A blueprint applies to a copy of its engine that was built turned, and sets the dials there. */
	@GameTest(template = "empty_large", timeoutTicks = 400)
	public static void aBlueprintAppliesToATurnedCopyOfItsEngine(GameTestHelper helper) throws BlueprintException {
		RecordedBlueprint recorded = recordedTestEngine(helper);
		ServerLevel level = helper.getLevel();
		Blueprint plain = BlueprintCodec.decode(recorded.text());
		Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
		BlockPos head = TestEngine.HEAD.subtract(TestEngine.PRIMARY);
		for (int k = 1; k < 4; k++) {
			net.minecraft.world.level.block.Rotation rotation = com.createengines.content.blueprint.BlueprintTurner.TURNS.get(k);
			net.minecraft.world.level.block.Mirror mirror = com.createengines.content.blueprint.BlueprintTurner.mirrorFor(plain, rotation);
			BlockPos site = helper.absolutePos(TURN_SITES[k]);
			BlueprintBuilder.build(player, level, site, recorded, rotation);
			int[] at = com.createengines.content.blueprint.BlueprintTurner.place(head.getX(), head.getZ(), rotation, mirror);
			BlockPos turnedHead = site.offset(at[0], head.getY(), at[1]);
			if (!(level.getBlockEntity(turnedHead) instanceof CylinderHeadBlockEntity entity))
				helper.fail(rotation + ": no cylinder head at the turned place " + turnedHead);
			else {
				entity.metering.setValue(3);
				BlueprintApplier.Result result = BlueprintApplier.apply(level, turnedHead, recorded);
				if (result.status() != BlueprintApplier.Status.APPLIED || entity.metering.getValue() != 14)
					helper.fail(rotation + " (" + mirror + "): applying gave " + result + " and the dial is " + entity.metering.getValue());
			}
		}
		helper.succeed();
	}
}
