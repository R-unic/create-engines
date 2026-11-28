package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.registry.CEItems;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Ponder scenes for the crank, the cylinders and the cams. Text here is the English source; datagen writes it to the lang file. */
public final class CrankScenes {

	private CrankScenes() {
	}

	public static void crankshaft(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("crankshaft", "Building a Crankshaft");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);
		scene.idle(10);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 6, 1, 3);
		Selection secondThrow = util.select()
			.position(4, 1, 3);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.idle(10);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A crankshaft is an unbroken line of Crankshaft and Crank Throw blocks on one axis. It turns like a Create shaft, but a plain Shaft in the middle splits it and the engine will not run.")
			.pointAt(util.vector()
				.topOf(1, 1, 3))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.idle(80);

		scene.overlay()
			.showText(70)
			.text("Crank Throws carry an offset crank pin. Cylinders mount on any of their four sides.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 1, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(80);

		Selection uprightCylinders = util.select()
			.fromTo(2, 2, 3, 4, 3, 3);
		scene.world()
			.showSection(uprightCylinders, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(80)
			.attachKeyFrame()
			.text("Each piston reaches the top of its stroke when the crank pin points up its bore.")
			.pointAt(util.vector()
				.topOf(2, 2, 3))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.setKineticSpeed(crank, 0);
		setThrow(scene, secondThrow, THROW_HALF_TURN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A throw's angle can be changed with its value panel. Here the second throw is turned 180°, so its piston peaks half a turn after the first. Spreading the throws like this is how firings are spaced out; see Firing Order and Smooth Running.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(4, 1, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(100);

		Selection sideBank = util.select()
			.fromTo(4, 1, 1, 4, 1, 2);
		scene.world()
			.showSection(sideBank, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("Cylinders on different faces of one throw share its pin. This makes a V layout: you never pick an engine type, you build it.")
			.pointAt(util.vector()
				.topOf(4, 1, 2))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("One crankshaft is one engine. It drives the kinetic network through the shafts and gears at its ends.")
			.pointAt(util.vector()
				.topOf(6, 1, 3))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void cylinder(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("cylinder", "Assembling a Cylinder");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		BlockPos cylinder = util.grid()
			.at(2, 2, 2);
		BlockPos head = util.grid()
			.at(2, 3, 2);
		Selection crank = util.select()
			.fromTo(1, 1, 2, 3, 1, 2);
		Selection cams = util.select()
			.fromTo(1, 4, 2, 3, 4, 2);

		scene.world()
			.modifyBlock(cylinder, s -> s.setValue(CylinderBlock.HAS_PISTON, false)
				.setValue(CylinderBlock.HAS_ROD, false), false);
		scene.world()
			.modifyBlock(head, s -> s.setValue(CylinderHeadBlock.HAS_INJECTOR, false), false);
		scene.idle(10);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(util.select()
				.position(cylinder), Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(70)
			.attachKeyFrame()
			.text("A Cylinder sits on a Crank Throw, its bore pointing away from the crank.")
			.pointAt(util.vector()
				.blockSurface(cylinder, Direction.NORTH))
			.placeNearTarget();
		scene.idle(80);

		scene.overlay()
			.showControls(util.vector()
				.topOf(cylinder), Pointing.DOWN, 40)
			.rightClick()
			.withItem(CEItems.CONNECTING_ROD.asStack());
		scene.idle(10);
		scene.world()
			.modifyBlock(cylinder, s -> s.setValue(CylinderBlock.HAS_ROD, true), false);
		scene.idle(40);
		scene.overlay()
			.showControls(util.vector()
				.topOf(cylinder), Pointing.DOWN, 40)
			.rightClick()
			.withItem(CEItems.PISTON.asStack());
		scene.idle(10);
		scene.world()
			.modifyBlock(cylinder, s -> s.setValue(CylinderBlock.HAS_PISTON, true), false);
		scene.overlay()
			.showText(80)
			.text("Install a Connecting Rod and an Engine Piston by right-clicking. Sneak-right-click with an empty hand to take them out.")
			.pointAt(util.vector()
				.blockSurface(cylinder, Direction.NORTH))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(util.select()
				.position(head), Direction.DOWN);
		scene.idle(10);
		scene.overlay()
			.showControls(util.vector()
				.topOf(head), Pointing.DOWN, 40)
			.rightClick()
			.withItem(CEItems.FUEL_INJECTOR.asStack());
		scene.idle(10);
		scene.world()
			.modifyBlock(head, s -> s.setValue(CylinderHeadBlock.HAS_INJECTOR, true), false);
		scene.overlay()
			.showText(80)
			.attachKeyFrame()
			.text("A Cylinder Head closes the bore. It holds the valves and a Fuel Injector, which must be installed.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(util.select()
				.position(2, 3, 1), Direction.SOUTH);
		scene.world()
			.showSection(util.select()
				.position(2, 3, 3), Direction.NORTH);
		scene.world()
			.showSection(cams, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("The head's other faces are its connections: an intake and an exhaust. A Cam Lobe touching the head, or the cylinder below it, works the valves and injector.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.WEST))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("Fuel is piped into any face of a head. Every head on the crankshaft shares its fuel, so one pipe can feed the whole engine.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("Engine Oil goes in the same way, into a second tank beside the fuel. It lubricates every moving part of the engine, not just this cylinder.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(cams, 16);
		setLobe(scene, util.select()
			.position(2, 4, 2), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.overlay()
			.showText(80)
			.text("Goggles on any engine part show whether that cylinder is firing, and if not, why.")
			.pointAt(util.vector()
				.blockSurface(cylinder, Direction.NORTH))
			.placeNearTarget();
		scene.idle(90);

		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("To stop the engine, put a redstone signal on its camshaft to cut the fuel. The Camshaft page shows how.")
			.pointAt(util.vector()
				.topOf(2, 4, 2))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void camshaft(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("camshaft", "Timing an Engine with Camshafts");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 2, 5, 1, 2);
		Selection engine = util.select()
			.fromTo(1, 2, 1, 2, 3, 2);
		Selection lobe = util.select()
			.position(2, 3, 3);
		Selection camshaft = util.select()
			.fromTo(3, 3, 3, 5, 3, 3);
		Selection gearing = util.select()
			.fromTo(5, 2, 3, 6, 3, 3);
		Selection camTrain = util.select()
			.fromTo(2, 3, 3, 5, 3, 3)
			.add(gearing);

		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(15);
		scene.world()
			.showSection(lobe, Direction.NORTH);
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Cam Lobe touching a cylinder head, or the cylinder block below it, opens its valves and fires its injector as the camshaft turns. Keep the camshaft unbroken: a plain Shaft between its blocks leaves the lobes cut off from it with no cam.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.WEST))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(camshaft, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(gearing, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("In a four-stroke engine the camshaft must turn at exactly half crank speed. Gear it from the crank, for example with a small cogwheel driving a large one.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(5, 2, 3), Direction.NORTH))
			.placeNearTarget();
		setLobe(scene, lobe, LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(camTrain, -16);
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("The Lobe Angle, set with the value panel, decides when the valves open compared with the piston. Goggles show if it is on time, early or late.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.setKineticSpeed(crank, 0);
		scene.world()
			.setKineticSpeed(camTrain, 0);
		setLobe(scene, lobe, LOBE_FOR_UPRIGHT_CYLINDER + LOBE_HALF_TURN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(camTrain, -16);
		scene.overlay()
			.showText(100)
			.text("The camshaft turns at half speed, so a lobe angle counts double. Two angles 180° apart both time the valves, but one fires the cylinder a whole crank turn after the other. That choice is how firing order is set; the next page shows how.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		// Shutting the engine down: a lever on the side of one camshaft block
		BlockPos lever = util.grid()
			.at(4, 3, 4);
		scene.world()
			.setBlock(lever, net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LeverBlock.FACE,
					net.minecraft.world.level.block.state.properties.AttachFace.WALL)
				.setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.SOUTH), false);
		scene.world()
			.showSection(util.select()
				.position(lever), Direction.NORTH);
		scene.idle(10);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Shutting the engine down: put a redstone signal, such as a lever, on any block of the camshaft line.")
			.pointAt(util.vector()
				.centerOf(lever))
			.placeNearTarget();
		scene.idle(40);
		scene.world()
			.modifyBlock(lever, s -> s.setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true), false);
		scene.effects()
			.indicateRedstone(lever);
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank, 16);
		scene.world()
			.setKineticSpeed(camTrain, -8);
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank, 6);
		scene.world()
			.setKineticSpeed(camTrain, -3);
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank, 0);
		scene.world()
			.setKineticSpeed(camTrain, 0);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.RED)
			.text("The signal's strength closes the fuel rack in proportion: a weak one trims the fuel, and 15 cuts it. The fuel stays in the pipes and tanks.")
			.pointAt(util.vector()
				.topOf(4, 3, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.text("An engine with two camshaft lines, such as a V or a flat engine with a cam for each bank, needs a signal on each line. A sensor's own output does not count.")
			.pointAt(util.vector()
				.topOf(4, 3, 3))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void firingOrder(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("firing_order", "Firing Order and Smooth Running");
		scene.configureBasePlate(0, 0, 8);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 6, 1, 3);
		Selection firstCylinder = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(util.select()
				.position(2, 3, 2))
			.add(util.select()
				.position(2, 3, 4));
		Selection otherCylinders = util.select()
			.fromTo(3, 2, 3, 5, 3, 3)
			.add(util.select()
				.fromTo(3, 3, 2, 5, 3, 2))
			.add(util.select()
				.fromTo(3, 3, 4, 5, 3, 4));
		Selection firstCam = util.select()
			.fromTo(1, 4, 3, 2, 4, 3);
		Selection otherCams = util.select()
			.fromTo(3, 4, 3, 6, 4, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 6, 4, 3);
		Selection flywheel = util.select()
			.position(7, 1, 3);

		// Cylinders 1 and 4 sit on throws at 0°, 2 and 3 on throws at 180°: a flat-plane crank. Every lobe starts on the
		// first crank turn, so 1 and 4 fire together and 2 and 3 fire together
		int[] throwSteps = {0, THROW_HALF_TURN, THROW_HALF_TURN, 0};
		for (int i = 0; i < 4; i++) {
			setThrow(scene, util.select()
				.position(2 + i, 1, 3), throwSteps[i]);
			setLobe(scene, util.select()
				.position(2 + i, 4, 3), lobeFor(throwSteps[i], Direction.UP, false));
		}
		scene.world()
			.setKineticSpeed(crank, TIMING_SPEED);
		scene.world()
			.setKineticSpeed(cam, TIMING_SPEED / 2);
		scene.world()
			.setKineticSpeed(flywheel, TIMING_SPEED);

		// One cylinder: a power stroke every 720°, and a lurch in between
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(firstCylinder, Direction.DOWN);
		scene.world()
			.showSection(firstCam, Direction.DOWN);
		scene.idle(25);
		scene.overlay()
			.showText(160)
			.attachKeyFrame()
			.text("A four-stroke cylinder gives one power stroke every two turns of the crank: 720°. The rest of the time it only drags on the crankshaft, so a lone cylinder makes the engine surge with each stroke and slow between them. That surge is the speed ripple.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		playFirings(scene, util, PonderPalette.WHITE, 2, new int[] {0}, new int[][] {{2}});
		scene.idle(5);

		// Four cylinders timed badly: two bunches and a long gap
		scene.world()
			.showSection(otherCylinders, Direction.DOWN);
		scene.world()
			.showSection(otherCams, Direction.DOWN);
		scene.idle(20);
		for (int i = 0; i < 4; i++)
			scene.overlay()
				.showText(45)
				.text(String.valueOf(i + 1))
				.pointAt(util.vector()
					.blockSurface(util.grid()
						.at(2 + i, 2, 3), Direction.NORTH))
				.placeNearTarget();
		scene.idle(50);
		scene.overlay()
			.showText(170)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("More cylinders only smooth the engine if they fire at different moments. As built, pistons 1 and 4 reach the top together and so do 2 and 3, so all four fire in two bunches 180° apart, then nothing for 540°. The speed ripple stays high.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		playFirings(scene, util, PonderPalette.RED, 2, new int[] {0, 180}, new int[][] {{3, 4}, {2, 5}});
		scene.idle(5);

		// The two dials: throw angle and lobe angle
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Two angles set when each cylinder fires. A crank throw's angle sets where in one turn its piston peaks: turn a throw, and its piston peaks that much sooner or later. Throws 2 and 3 here are turned 180°, so their pistons peak half a turn after 1 and 4.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(3, 1, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(160);

		setLobe(scene, util.select()
			.position(4, 4, 3), lobeFor(THROW_HALF_TURN, Direction.UP, true));
		setLobe(scene, util.select()
			.position(5, 4, 3), lobeFor(0, Direction.UP, true));
		scene.overlay()
			.showText(170)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A cam lobe's angle sets which turn. The camshaft runs at half speed, so a lobe angle counts double: a lobe 180° further round times its cylinder just as well, but fires it a whole crank turn later. Cylinders 3 and 4 are moved on to the second turn.")
			.pointAt(util.vector()
				.topOf(4, 4, 3))
			.placeNearTarget();
		playFirings(scene, util, PonderPalette.GREEN, 2, new int[] {0, 180, 360, 540},
			new int[][] {{3}, {2}, {4}, {5}});
		scene.idle(5);

		// Even spacing
		scene.overlay()
			.showText(170)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Now they fire one after another in the order 1, 3, 4, 2, a power stroke every 180°. Some cylinder is always pushing, so the speed hardly moves. Aim for 720° ÷ cylinders between firings: 180° for four, 120° for six, 90° for eight.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		playFirings(scene, util, PonderPalette.GREEN, 2, new int[] {0, 180, 360, 540},
			new int[][] {{3}, {2}, {4}, {5}});
		scene.idle(5);

		// Goggles
		scene.overlay()
			.showText(150)
			.text("Goggles on a crankshaft show the firing intervals and the Speed ripple, which turns gold when the engine runs rough. Goggles on a cylinder show the crank angle its piston peaks at.")
			.pointAt(util.vector()
				.topOf(1, 1, 3))
			.placeNearTarget();
		scene.idle(160);

		// Mass smooths what spacing leaves
		scene.world()
			.showSection(flywheel, Direction.WEST);
		scene.idle(15);
		scene.overlay()
			.showText(170)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.text("Mass smooths the rest. A Create Flywheel on the crankshaft's axis adds rotating inertia, so each power stroke changes the speed less and the engine carries through its compression strokes. Even spacing and enough inertia together give a low speed ripple.")
			.pointAt(util.vector()
				.topOf(7, 1, 3))
			.placeNearTarget();
		playFirings(scene, util, PonderPalette.GREEN, 2, new int[] {0, 180, 360, 540},
			new int[][] {{3}, {2}, {4}, {5}});
		scene.idle(5);

		// Planning by hand
		scene.overlay()
			.showText(190)
			.attachKeyFrame()
			.text("To plan one by hand, read each piston's top dead centre angle from goggles. Its firing point in the 720° cycle is that angle, plus 360° if its lobe is on the second turn, and the lobe angle is half the firing point. Choose them so the points fall 720° ÷ cylinders apart. A Timing Tool does all of this for you.")
			.pointAt(util.vector()
				.topOf(5, 4, 3))
			.placeNearTarget();
		scene.idle(200);
		scene.markAsFinished();
	}

	public static void compactV(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("compact_v", "Packing a V Engine");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 4, 2, 3);
		Selection upright = util.select()
			.fromTo(2, 3, 3, 3, 4, 3);
		Selection uprightPipes = util.select()
			.fromTo(2, 4, 2, 3, 4, 2)
			.add(util.select()
				.fromTo(2, 4, 4, 3, 4, 4));
		Selection north = util.select()
			.fromTo(2, 2, 1, 3, 2, 2);
		Selection northPipes = util.select()
			.fromTo(2, 3, 1, 3, 3, 1)
			.add(util.select()
				.fromTo(2, 1, 1, 3, 1, 1));
		// The camshaft sits in the valley, against the upright bank's cylinders on one side and the north bank's on the other
		Selection valleyCam = util.select()
			.fromTo(1, 3, 2, 4, 3, 2);

		// The second throw is turned half way round, as in a crossplane V. Each valley lobe is set for the upright cylinder
		// of its throw, the first of the two it works; the north cylinder follows its own piston
		int[] throwSteps = {0, THROW_HALF_TURN};
		for (int i = 0; i < 2; i++) {
			setThrow(scene, util.select()
				.position(2 + i, 2, 3), throwSteps[i]);
			setLobe(scene, util.select()
				.position(2 + i, 3, 2), lobeFor(throwSteps[i], Direction.UP, false));
		}
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(upright, Direction.DOWN);
		scene.world()
			.showSection(north, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("A V is two banks of cylinders on the same crank throws: here one bank points up and the other north.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.showSection(uprightPipes, Direction.DOWN);
		scene.world()
			.showSection(northPipes, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Every head needs an intake and an exhaust, and every head shares fuel, so a tight bank soon runs out of free faces for a camshaft as well.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 4, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(valleyCam, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(valleyCam, 16);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Cam Lobe works a cylinder from beside its block as well as beside its head, through pushrods. Put the camshaft in the valley, against the cylinder blocks of both banks, and the heads keep every face for pipes.")
			.pointAt(util.vector()
				.centerOf(2, 3, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("One lobe works the cylinders on both banks. Its angle times the first of them, and the others follow their own crank angles, so a V needs one camshaft and one lobe to set for each throw. Goggles show when a cylinder is on time.")
			.pointAt(util.vector()
				.centerOf(3, 3, 2))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(100)
			.text("Add a throw with a cylinder on each side for every two cylinders: six throws make a V12.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("It is also how a big engine stays stiff. A long crank twists under load: past eight throws on one crankshaft it costs rating, about 2.5 percent a throw. A V12 has six throws and loses nothing, where a straight twelve has twelve. Cranks geared together one to one count as separate lines.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		scene.idle(160);
		scene.markAsFinished();
	}
}
