package com.createengines.ponder;

import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.CrankGeometry;
import com.createengines.engine.TurboStatus;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Ponder scenes for the engine components. Text here is the English source; datagen writes it to the lang file. */
public final class EngineScenes {

	/** Lobe angle step that times a cylinder bored straight up from a throw at 0° on an X-axis crank. */
	private static final int LOBE_FOR_UPRIGHT_CYLINDER = 18;
	private static final int THROW_HALF_TURN = 180 / CrankshaftBlockEntity.THROW_ANGLE_STEP;
	private static final int LOBE_HALF_TURN = (int) (180 / CamshaftBlockEntity.LOBE_ANGLE_STEP);

	private EngineScenes() {
	}

	private static void setThrow(CreateSceneBuilder scene, Selection selection, int value) {
		scene.world()
			.modifyBlockEntityNBT(selection, CrankshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", value));
	}

	private static void setLobe(CreateSceneBuilder scene, Selection selection, int value) {
		scene.world()
			.modifyBlockEntityNBT(selection, CamshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", value));
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

		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A redstone signal on the camshaft closes the fuel rack. A full-strength signal cuts the fuel and stops the engine.")
			.pointAt(util.vector()
				.topOf(4, 3, 3))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	/**
	 * The lobe step that times a cylinder bored along {@code bore} from a throw set to {@code throwSteps}, so that the
	 * scene's angles are worked out the way a player works them out. {@code secondTurn} is the lobe 180° on, which fires
	 * the cylinder a crank revolution later.
	 */
	private static int lobeFor(int throwSteps, Direction bore, boolean secondTurn) {
		double tdc = CrankGeometry.topDeadCentreAngle(Axis.X, throwSteps * CrankshaftBlockEntity.THROW_ANGLE_STEP, bore);
		double lobe = ((tdc / 2) % 180 + 180) % 180 + (secondTurn ? 180 : 0);
		int steps = (int) (360 / CamshaftBlockEntity.LOBE_ANGLE_STEP);
		return (int) Math.round(lobe / CamshaftBlockEntity.LOBE_ANGLE_STEP) % steps;
	}

	/** Crank speed of the firing pages. 7.2° a tick makes a 720° four-stroke cycle exactly 100 ticks, 180° exactly 25. */
	private static final int TIMING_SPEED = 24;
	private static final float TIMING_DEGREES_PER_TICK = TIMING_SPEED * 3 / 10f;
	private static final int FLASH_TICKS = 10;

	/** Outlines the cylinder and head in the given column of the inline four, as a power stroke. */
	private static void flash(CreateSceneBuilder scene, SceneBuildingUtil util, PonderPalette color, int column) {
		scene.overlay()
			.showOutline(color, "firing" + column, util.select()
				.fromTo(column, 2, 3, column, 3, 3), FLASH_TICKS);
	}

	/**
	 * Flashes cylinders at the crank angles they fire at, in real time for the page's crank speed, for whole 720°
	 * cycles. {@code angles} ascend within one cycle and {@code columns[i]} are the cylinders firing at {@code angles[i]}.
	 * The flashes keep the spacing the page is about but are not locked to the pistons drawn.
	 */
	private static void playFirings(CreateSceneBuilder scene, SceneBuildingUtil util, PonderPalette color, int cycles,
		int[] angles, int[][] columns) {
		int clock = 0;
		for (int cycle = 0; cycle < cycles; cycle++)
			for (int i = 0; i < angles.length; i++) {
				int target = cycle * 720 + angles[i];
				int wait = Math.round((target - clock) / TIMING_DEGREES_PER_TICK);
				if (wait > 0)
					scene.idle(wait);
				clock = target;
				for (int column : columns[i])
					flash(scene, util, color, column);
			}
		int rest = Math.round((cycles * 720 - clock) / TIMING_DEGREES_PER_TICK);
		if (rest > 0)
			scene.idle(rest);
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

	public static void airflow(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("airflow", "Intake and Exhaust");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 4, 1, 3);
		Selection cylinders = util.select()
			.fromTo(2, 2, 3, 3, 3, 3)
			.add(util.select()
				.fromTo(1, 4, 3, 4, 4, 3));
		Selection intake = util.select()
			.fromTo(1, 3, 2, 3, 3, 2);
		Selection exhaust = util.select()
			.fromTo(2, 3, 4, 3, 4, 4);
		BlockPos airIntake = util.grid()
			.at(1, 3, 2);

		setThrow(scene, util.select()
			.position(3, 1, 3), THROW_HALF_TURN);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(cylinders, Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(70)
			.attachKeyFrame()
			.text("Every cylinder head needs an intake and an exhaust on its free faces.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(80);

		scene.world()
			.showSection(intake, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("Intake Manifolds are pipes. They connect to the heads, pipes and openings beside them, and run from the heads to an Air Intake.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 2), Direction.NORTH))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.showSection(exhaust, Direction.NORTH);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.text("Exhaust Manifolds do the same for an Exhaust Outlet, as pipes can bend and join like a real exhaust. Each opening comfortably serves two cylinders.")
			.pointAt(util.vector()
				.topOf(3, 4, 4))
			.placeNearTarget();
		scene.idle(100);
		scene.overlay()
			.showText(90)
			.text("A Wrench cuts or restores the pipe connection on the side you click, so two runs can lie side by side without joining.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 4), Direction.UP))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(crank, 48);
		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 4, 3, 4, 4, 3), 24);
		scene.world()
			.setBlock(airIntake.west(), Blocks.STONE.defaultBlockState(), false);
		scene.overlay()
			.showOutline(PonderPalette.RED, airIntake, util.select()
				.position(airIntake.west()), 80);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Openings need a clear mouth. Covering the air intake starves every cylinder on it; an engine that cannot exhaust chokes on its own burned gas.")
			.pointAt(util.vector()
				.blockSurface(airIntake, Direction.WEST))
			.placeNearTarget();
		scene.idle(100);
		scene.world()
			.setBlock(airIntake.west(), Blocks.AIR.defaultBlockState(), false);
		scene.idle(10);

		scene.overlay()
			.showText(90)
			.text("Smoke from the outlet follows each firing. Black smoke means more fuel than air: the engine is running rich. Turn down the Fuel Metering on its cylinder heads.")
			.pointAt(util.vector()
				.topOf(3, 4, 4))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void spark(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("spark", "Spark Ignition and Fuel Metering");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection alternator = util.select()
			.position(0, 1, 3);
		Selection battery = util.select()
			.position(1, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 4))
			.add(util.select()
				.fromTo(1, 4, 3, 3, 4, 3));
		BlockPos head = util.grid()
			.at(2, 3, 3);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A Spark Plug installed in a Cylinder Head lights the fuel with a spark, instead of waiting for compression to heat it. Gasoline is too hard to light by compression alone, so it needs one.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(battery, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Plugs fire from a Battery touching the crankshaft or a cylinder head. Each spark uses a little charge, and a flat battery gives no spark.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(alternator, Direction.EAST);
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank.add(alternator), 48);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A battery makes no charge itself. An Alternator charges the batteries touching it, and those of the engine it touches, while its shaft turns, for a little stress.")
			.pointAt(util.vector()
				.topOf(0, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.text("On the crank's own shaft line, cranking charges the battery and the running engine keeps it topped up.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Each head's injector is metered by hand. A cylinder breathes less air as it speeds up, so a setting that is right at one speed is rich above it: fuel wasted, black smoke, less power. Too little runs lean.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.text("A spark can only light a mixture that is neither too lean nor too rich. Tune Fuel Metering to the speed the engine runs at, reading each cylinder's mixture through goggles.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void mafSensor(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("maf_sensor", "Matching Fuel to Air with a MAF Sensor");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam)
			.add(util.select()
				.position(2, 3, 4));
		Selection airIntake = util.select()
			.position(2, 3, 1);
		Selection body = util.select()
			.position(2, 3, 2);
		Selection lamp = util.select()
			.position(3, 3, 2);
		BlockPos sensor = util.grid()
			.at(2, 3, 2);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(airIntake, Direction.SOUTH);
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.world()
			.setKineticSpeed(cam, 48);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.RED)
			.text("An injector delivers the shot it was set to, however much air the cylinder breathed. Breathing falls as the engine speeds up, so a shot that suits slow running is rich at speed.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(body, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A MAF Sensor is a length of intake pipe that weighs the air each cylinder behind it breathes.")
			.pointAt(util.vector()
				.topOf(sensor))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Every injector on its network is given the fuel that air can burn, whichever way that moves the shot: trimmed back as the engine speeds up and the air thins, topped up as it slows. Fuel Metering no longer matters, and the injector can give at most twice its usual shot.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(sensor, Direction.WEST), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its Mixture Target, set with the value panel, is how lean or rich it holds the shot: 80% to 125% of balanced.")
			.pointAt(util.vector()
				.blockSurface(sensor, Direction.WEST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(lamp, Direction.DOWN);
		scene.idle(15);
		scene.world()
			.modifyBlock(util.grid()
				.at(3, 3, 2), state -> state.setValue(BlockStateProperties.LIT, true), false);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(3, 3, 2));
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("It also puts the size of the breath out as redstone: 15 for a boosted cylinder half again as full as its bore. Goggles show the air charge and how much fuel is being cut or added.")
			.pointAt(util.vector()
				.topOf(3, 3, 2))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void governor(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("governor", "Holding an Engine at a Speed");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam)
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 2))
			.add(util.select()
				.position(2, 3, 4));
		Selection governor = util.select()
			.position(3, 2, 3);
		Selection lamp = util.select()
			.fromTo(4, 2, 3, 5, 2, 3);
		BlockPos block = util.grid()
			.at(3, 2, 3);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("An engine's speed depends on its load: let go of it and it races, load it and it lugs down. Everything it drives runs at whatever pace that happens to be.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crank, 112);
		scene.world()
			.setKineticSpeed(cam, 56);
		scene.idle(120);

		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.world()
			.showSection(governor, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A Governor set against a crankshaft block or a cylinder head moves the fuel rack to hold the crank at a set speed: it eases the fuel off as the engine speeds up and opens it as it slows.")
			.pointAt(util.vector()
				.topOf(block))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(block, Direction.NORTH), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its Governed Speed is set with the value panel, in steps of 5 RPM, up to 1000. A spark engine is governed on its throttle instead, because a charge that is too lean will not light.")
			.pointAt(util.vector()
				.blockSurface(block, Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.GREEN)
			.text("It holds the speed with the torque in hand, so the engine's rating is untouched. If the load is more than the engine can carry at that speed, the rack stays wide open and the engine slows.")
			.pointAt(util.vector()
				.topOf(block))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(lamp, Direction.DOWN);
		scene.idle(15);
		scene.world()
			.modifyBlock(util.grid()
				.at(5, 2, 3), state -> state.setValue(BlockStateProperties.LIT, true), false);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(5, 2, 3));
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A comparator reads how far open the rack is held, 15 wide open and 0 shut, so it works as a load meter. The Governor never powers its neighbours directly.")
			.pointAt(util.vector()
				.topOf(4, 2, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Power the Governor with redstone and it holds only that share of its setting: strength 15 is all of it, 8 about half, never below 20 RPM. A circuit can command an engine's speed.")
			.pointAt(util.vector()
				.topOf(block))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(110)
			.text("It also eases the fuel off whenever the engine would race, so an unloaded governed engine idles on a fraction of the fuel a racing one burns.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void timingTool(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("timing_tool", "Setting a Firing Order");
		scene.configureBasePlate(0, 0, 8);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 6, 1, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 5, 3, 3)
			.add(util.select()
				.fromTo(2, 3, 2, 5, 3, 2))
			.add(util.select()
				.fromTo(2, 3, 4, 5, 3, 4));
		Selection cam = util.select()
			.fromTo(1, 4, 3, 6, 4, 3);

		int[] throwSteps = {0, THROW_HALF_TURN, THROW_HALF_TURN, 0};
		for (int i = 0; i < 4; i++) {
			setThrow(scene, util.select()
				.position(2 + i, 1, 3), throwSteps[i]);
			setLobe(scene, util.select()
				.position(2 + i, 4, 3), lobeFor(throwSteps[i], Direction.UP, false));
		}
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(cam, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(cam, 16);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Set by hand, every crank throw and every cam lobe is a value to work out. Built like this, the cylinders fire in pairs and the engine is lumpy.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(util.grid()
					.at(3, 1, 3), Direction.NORTH), Pointing.RIGHT, 50)
			.rightClick()
			.withItem(CEItems.TIMING_TOOL.asStack());
		scene.idle(15);
		setLobe(scene, util.select()
			.position(4, 4, 3), lobeFor(THROW_HALF_TURN, Direction.UP, true));
		setLobe(scene, util.select()
			.position(5, 4, 3), lobeFor(0, Direction.UP, true));
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Use a Timing Tool on any block of the engine. It works out the most even firing the layout allows and sets every throw and lobe to it, then tells you the firing order.")
			.pointAt(util.vector()
				.topOf(4, 4, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Sneak-use it to change what it may set: any throw angle (smooth), quarter turns (cross-plane V8), half turns (flat-plane), or cams only, which leaves the crank as built and just times the lobes.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.WHITE)
			.text("Some layouts can't fire evenly, such as a 90° V12, and a cam lobe shared by two cylinders can only time one of them. The tool says so, and gets as close as it can.")
			.pointAt(util.vector()
				.topOf(5, 4, 3))
			.placeNearTarget();
		scene.idle(130);
		scene.markAsFinished();
	}

	public static void coupledCranks(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("coupled_cranks", "Coupled Crankshafts");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crankA = util.select()
			.fromTo(1, 1, 2, 3, 1, 2);
		Selection crankB = util.select()
			.fromTo(1, 1, 3, 4, 1, 3);
		Selection engineA = util.select()
			.fromTo(2, 2, 2, 2, 3, 2)
			.add(util.select()
				.position(2, 3, 1))
			.add(util.select()
				.position(1, 3, 2));
		Selection engineB = util.select()
			.fromTo(3, 2, 3, 3, 3, 3)
			.add(util.select()
				.position(3, 3, 4))
			.add(util.select()
				.position(4, 3, 3));
		Selection camA = util.select()
			.fromTo(1, 4, 2, 3, 4, 2);
		Selection camB = util.select()
			.fromTo(2, 4, 3, 4, 4, 3);
		Selection gears = util.select()
			.fromTo(0, 1, 2, 0, 1, 3);

		setThrow(scene, util.select()
			.position(2, 1, 2), 0);
		setThrow(scene, util.select()
			.position(3, 1, 3), THROW_HALF_TURN);
		setLobe(scene, util.select()
			.position(2, 4, 2), LOBE_FOR_UPRIGHT_CYLINDER);
		setLobe(scene, util.select()
			.position(3, 4, 3), lobeFor(THROW_HALF_TURN, Direction.UP, true));

		scene.world()
			.showSection(crankA, Direction.DOWN);
		scene.world()
			.showSection(engineA, Direction.DOWN);
		scene.world()
			.showSection(camA, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(crankB, Direction.DOWN);
		scene.world()
			.showSection(engineB, Direction.DOWN);
		scene.world()
			.showSection(camB, Direction.DOWN);
		scene.idle(15);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A crankshaft is an engine. A second line of crank blocks beside it is a second engine, however close: each has its own speed and its own firing.")
			.pointAt(util.vector()
				.topOf(3, 1, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(gears, Direction.EAST);
		scene.idle(10);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Gear them together one to one and they become one engine, sharing a single set of revs. Any Create gearing does it: cogwheels, chain drives, gearboxes, belts.")
			.pointAt(util.vector()
				.centerOf(0, 1, 2))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crankA, 32);
		scene.world()
			.setKineticSpeed(gears, 32);
		scene.world()
			.setKineticSpeed(crankB, -32);
		scene.world()
			.setKineticSpeed(camA, 16);
		scene.world()
			.setKineticSpeed(camB, -16);
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Two meshed cogwheels turn the second crank the other way; the engine allows for it. Each throw's angle still sets its own piston's place in the cycle, and the Timing Tool sets the throws and lobes on every line at once.")
			.pointAt(util.vector()
				.topOf(3, 1, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Fuel, oil, batteries, sensors and every cylinder on either crank belong to the one engine. It is rated and fired as a whole, so wide W, H and X layouts, or more cylinders than one crank can carry, are just more lines.")
			.pointAt(util.vector()
				.topOf(2, 3, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.WHITE)
			.text("Only an exact one-to-one ratio couples. Cranks geared two to one, say, stay separate engines feeding one network, and the faster one wins. Goggles show how many crankshafts an engine has.")
			.pointAt(util.vector()
				.centerOf(0, 1, 3))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void precisionParts(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("precision_parts", "Precision Parts");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 2, 3, 1, 2);
		Selection cams = util.select()
			.fromTo(1, 4, 2, 3, 4, 2);
		Selection cylinder = util.select()
			.position(2, 2, 2);
		Selection head = util.select()
			.position(2, 3, 2);
		Selection pipes = util.select()
			.position(2, 3, 1)
			.add(util.select()
				.position(2, 3, 3));

		setThrow(scene, util.select()
			.position(2, 1, 2), 0);
		setLobe(scene, util.select()
			.position(2, 4, 2), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(cams, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(cylinder, Direction.DOWN);
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Precision Cylinder has a machined liner. It squeezes the charge harder and rubs less, so each stroke makes more work for the same fuel.")
			.pointAt(util.vector()
				.topOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(head, Direction.DOWN);
		scene.world()
			.showSection(pipes, Direction.DOWN);
		scene.idle(10);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A Precision Cylinder Head has four valves and a pent roof. It breathes about half as freely again, which matters most at speed, and lights the charge sooner.")
			.pointAt(util.vector()
				.topOf(2, 3, 2))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cams, 32);
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Two parts are installed like their ordinary versions: a Forged Piston, which is lighter and drags less, and a Precision Injector, which atomises the fuel so it ignites sooner and burns faster.")
			.pointAt(util.vector()
				.topOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.GREEN)
			.text("Each part helps on its own and they add up. A fully precision engine rates well over half as much again as a crude one, and a turbocharger multiplies that. They are made from Precision Mechanisms.")
			.pointAt(util.vector()
				.topOf(2, 3, 2))
			.placeNearTarget();
		scene.idle(130);
		scene.markAsFinished();
	}

	public static void throttle(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("throttle", "Throttling an Engine");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam)
			.add(util.select()
				.position(2, 3, 4));
		Selection airIntake = util.select()
			.position(2, 3, 1);
		Selection body = util.select()
			.position(2, 3, 2);
		Selection pedal = util.select()
			.fromTo(3, 2, 2, 3, 3, 2);
		BlockPos throttle = util.grid()
			.at(2, 3, 2);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(airIntake, Direction.SOUTH);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.world()
			.showSection(body, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A Throttle Body is a length of intake pipe with a valve in it. Close it and every cylinder behind it draws less air.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(throttle, Direction.WEST), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its Idle Stop, set with the value panel, is how far open the valve rests with no signal.")
			.pointAt(util.vector()
				.blockSurface(throttle, Direction.WEST))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crank, 24);
		scene.world()
			.setKineticSpeed(cam, 12);
		scene.idle(110);

		scene.world()
			.showSection(pedal, Direction.DOWN);
		scene.idle(15);
		scene.world()
			.toggleRedstonePower(pedal);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(3, 3, 2));
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.world()
			.setKineticSpeed(cam, 48);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Redstone opens it, evenly from the idle stop at no signal to wide open at full strength. A lever, comparator or pedal works like a gas pedal.")
			.pointAt(util.vector()
				.topOf(3, 3, 2))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.toggleRedstonePower(pedal);
		scene.world()
			.setKineticSpeed(crank, 24);
		scene.world()
			.setKineticSpeed(cam, 12);
		scene.overlay()
			.showText(110)
			.text("Less air means less power: a throttled engine runs slower and has less torque to spare. Nearly shut, the piston spends more dragging air through the valve than it earns.")
			.pointAt(util.vector()
				.topOf(2, 2, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Its linkage trims every injector's fuel along with the air, so closing it does not flood the engine. Fuel Metering is still tuned for the speed the engine runs at.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(90)
			.text("Throttle bodies in one intake line act together: the most closed one governs every cylinder behind it.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void mixture(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("mixture", "Tuning the Fuel Mixture");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam)
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 2))
			.add(util.select()
				.position(2, 3, 4));
		BlockPos head = util.grid()
			.at(2, 3, 3);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 48);
		scene.world()
			.setKineticSpeed(cam, 24);
		scene.idle(20);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("A Fuel Injector delivers the same shot however much air the cylinder drew in. Fuel Metering sets that shot as a share of the usual one.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(head, Direction.EAST), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Turn the dial on the cylinder head's value panel. Each head has its own.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(crank, 128);
		scene.world()
			.setKineticSpeed(cam, 64);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A cylinder breathes less air the faster it turns, so a setting that is right at one speed is rich above it: fuel wasted, black smoke, less power.")
			.pointAt(util.vector()
				.topOf(2, 3, 4))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.setKineticSpeed(crank, 16);
		scene.world()
			.setKineticSpeed(cam, 8);
		scene.overlay()
			.showText(100)
			.colored(PonderPalette.RED)
			.text("Too little fuel is just as bad. A lean cylinder is weak, and a spark engine cannot light a mixture that is too thin or too rich.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.setKineticSpeed(crank, 48);
		scene.world()
			.setKineticSpeed(cam, 24);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Goggles on a cylinder read its air-fuel mixture. Balanced is the target; 2× rich means twice the fuel the air can burn, and 2× lean means half.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Scale Fuel Metering by the reading. At 2× rich, halve it; at 2× lean, double it. Do it with the engine at the speed and load it will really work at, and check again. Goggles suggest the figure when the mixture is off, and a Timing Tool in tune mode sets every injector for you, at the speed the engine is turning.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.GREEN)
			.text("Anything that changes the air changes the mixture. A turbocharger packs in more air, so raise the metering to match. A Throttle Body cuts the air and trims the shot with it.")
			.pointAt(util.vector()
				.topOf(2, 3, 4))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(90)
			.text("To cut fuel to every injector at once, power the camshaft with redstone. A Throttle Body trims it with the air.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void lubrication(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("lubrication", "Keeping an Engine Oiled");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		BlockPos head = util.grid()
			.at(2, 3, 3);
		BlockPos tank = util.grid()
			.at(4, 3, 3);
		scene.world()
			.modifyBlockEntity(util.grid()
				.at(4, 3, 3), FluidTankBlockEntity.class, be -> be.getTankInventory()
					.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
						.getSource(), 4000), FluidAction.EXECUTE));
		scene.idle(10);

		scene.world()
			.showSection(util.select()
				.fromTo(1, 1, 3, 2, 3, 3), Direction.DOWN);
		scene.idle(10);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("Crankshaft bearings, pistons and connecting rods all rub against something. They need a film of oil to slide.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 2, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(100)
			.colored(PonderPalette.RED)
			.text("A new engine is dry. Dry parts drag: an engine with no oil still runs, but tops out at about half its speed and has less torque to spare.")
			.pointAt(util.vector()
				.topOf(2, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(util.select()
				.fromTo(3, 3, 3, 4, 3, 3), Direction.WEST);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Pipe Engine Oil into any face of a Cylinder Head. It has a second tank for oil, apart from the fuel. Every head on the crankshaft shares it.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.setKineticSpeed(crank, 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Once oil reaches the engine, the film builds within seconds and friction drops. The engine can turn faster and has more torque to spare.")
			.pointAt(util.vector()
				.topOf(2, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.text("Turning wears the oil off a little at a time. If the supply runs dry the film thins and the engine slows, so keep the tank topped up.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(90)
			.text("Goggles on any crankshaft block show how well the engine is lubricated.")
			.pointAt(util.vector()
				.topOf(1, 1, 3))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("Engine Oil is made by heating Plant Oil with a Slime Ball in a Mechanical Mixer over a Blaze Burner.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.EAST))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void turbocharger(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("turbocharger", "Forced Induction");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam);
		Selection turbo = util.select()
			.position(1, 3, 3);
		Selection airIntake = util.select()
			.position(0, 3, 3);
		Selection outlet = util.select()
			.position(1, 3, 4);
		Selection intakePipes = util.select()
			.fromTo(1, 3, 2, 2, 3, 2);
		Selection redstone = util.select()
			.position(1, 2, 3);
		BlockPos turboPos = util.grid()
			.at(1, 3, 3);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.world()
			.showSection(turbo, Direction.EAST);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Turbocharger sits in the exhaust line and the intake line at once. Exhaust spins its turbine, and the compressor on the shaft's other end pushes air into the engine.")
			.pointAt(util.vector()
				.topOf(turboPos))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(outlet, Direction.NORTH);
		scene.idle(10);
		scene.overlay()
			.showText(100)
			.text("Exhaust pipes join its turbine face and its sides. Here the head's exhaust blows straight into the turbine, and an Exhaust Outlet carries it away.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(airIntake, Direction.EAST);
		scene.world()
			.showSection(intakePipes, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Intake pipes join its compressor face and its sides. Air comes in at the front and leaves by a pipe to the head, so every cylinder on that line is boosted.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.modifyBlockEntityNBT(turbo, TurbochargerBlockEntity.class,
				nbt -> nbt.put("Status", new TurboStatus(0.7f, 0.6f, 0.1f, 1.3f, true, true).write()));
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.world()
			.setKineticSpeed(cam, 48);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("It runs on the heat of the exhaust, so it spools up a moment after the engine fires and fades if the fuel stops. Goggles show its boost and wheel speed.")
			.pointAt(util.vector()
				.topOf(turboPos))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.NORTH), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("More air only helps if there is fuel for it. Turn the head's Fuel Metering up to burn it; left alone, the same shot just runs leaner.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.RED)
			.text("Squeezing air heats it, and the turbine holds the exhaust up, so the cylinders push against back-pressure. Under hard boost a spark engine's gasoline can light before the spark.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(turboPos, Direction.NORTH), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its wastegate vents exhaust round the turbine to hold the boost at the limit set on its value panel.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(redstone, Direction.UP);
		scene.idle(10);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(1, 2, 3));
		scene.world()
			.modifyBlockEntityNBT(turbo, TurbochargerBlockEntity.class,
				nbt -> nbt.put("Status", new TurboStatus(0.1f, 0.02f, 1f, 1.01f, true, true).write()));
		scene.overlay()
			.showText(100)
			.text("A redstone signal on the turbocharger opens the wastegate fully and dumps the boost, like a blow-off valve.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.DOWN))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void supercharger(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("supercharger", "Superchargers, Intercoolers and Stacked Boost");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam);
		Selection outlet = util.select()
			.position(1, 3, 3);
		Selection blower = util.select()
			.position(2, 3, 2);
		Selection driven = util.select()
			.fromTo(2, 3, 2, 4, 3, 2);
		Selection drive = util.select()
			.fromTo(3, 3, 2, 4, 3, 2);
		Selection cooler = util.select()
			.position(2, 3, 1);
		Selection airIntake = util.select()
			.position(2, 3, 0);
		BlockPos blowerPos = util.grid()
			.at(2, 3, 2);
		BlockPos coolerPos = util.grid()
			.at(2, 3, 1);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(outlet, Direction.EAST);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.world()
			.showSection(blower, Direction.SOUTH);
		scene.world()
			.showSection(airIntake, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A Supercharger is a blower on a shaft. Intake pipes join the faces round the shaft, so it sits in the intake run, or bolts straight to a head.")
			.pointAt(util.vector()
				.topOf(blowerPos))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.showSection(drive, Direction.WEST);
		scene.world()
			.setKineticSpeed(driven, 128);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("It needs no exhaust and has no lag. Its boost goes with the square of its shaft speed: 0.9 bar at 128 RPM, a quarter of that at half the speed.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.RED)
			.text("The price is stress. Turning the blower loads its shaft in proportion to the cylinders it feeds and the boost it makes, so geared from the crank's own shaft line it comes out of the engine's output.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(blowerPos, Direction.UP), Pointing.DOWN, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A bypass valve holds the boost at the limit set on its value panel; gear the shaft for the speed you want. Redstone opens the bypass fully and dumps the boost.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.UP))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(cooler, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Squeezing air heats it, and a blower heats it more than a turbo does. An Intercooler in the run, after the compressors, takes 60 percent of that heat back out.")
			.pointAt(util.vector()
				.topOf(coolerPos))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.GREEN)
			.text("Cooler air is denser, so the same boost packs in more of it, and a charge that starts cooler is further from lighting before the spark. The core costs about 4 percent of the boost in pressure.")
			.pointAt(util.vector()
				.blockSurface(coolerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Stages in series compound. When all the air has to pass through two compressors in turn, their pressures multiply, and each turbine in the exhaust adds its back-pressure. Stages side by side, with a branch the air can take round either, share the flow: the cylinders get their average pressure, and the smaller wheels spool sooner.")
			.pointAt(util.vector()
				.topOf(blowerPos))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void transmission(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("transmission", "Gearing with a Transmission");
		scene.configureBasePlate(0, 0, 8);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection input = util.select()
			.fromTo(0, 1, 2, 1, 1, 2);
		Selection box = util.select()
			.position(2, 1, 2);
		Selection output = util.select()
			.fromTo(3, 1, 2, 5, 1, 2);
		Selection lever = util.select()
			.position(2, 1, 3);
		BlockPos boxPos = util.grid()
			.at(2, 1, 2);

		scene.world()
			.showSection(input, Direction.DOWN);
		scene.world()
			.setKineticSpeed(input, 64);
		scene.idle(10);
		scene.world()
			.showSection(box, Direction.DOWN);
		scene.world()
			.showSection(output, Direction.DOWN);
		scene.world()
			.setKineticSpeed(box, 64);
		scene.world()
			.setKineticSpeed(output, 5);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Transmission is a gearbox with selectable gears. Rotation goes in at one end of its shaft and comes out of the other at the speed of the gear it is in.")
			.pointAt(util.vector()
				.topOf(boxPos))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("In first gear the output turns twelve times slower than the input. A load behind it asks the engine for a twelfth of the torque it would direct, so an engine can carry a far heavier load, slowly.")
			.pointAt(util.vector()
				.topOf(4, 1, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(boxPos, Direction.NORTH), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.world()
			.setKineticSpeed(output, 28);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("The value panel picks the gear, from first to thirteenth, each about a quarter faster than the one below. Ninth turns the output at a little over two fifths of the input...")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.setKineticSpeed(output, 64);
		scene.overlay()
			.showText(90)
			.text("...and thirteenth drives it direct. The fastest output, but the load asks the crank for its full torque.")
			.pointAt(util.vector()
				.topOf(4, 1, 2))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(output, 0);
		scene.overlay()
			.showText(90)
			.text("Neutral lets nothing through, so an engine can start with no load on it.")
			.pointAt(util.vector()
				.topOf(4, 1, 2))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(output, -21);
		scene.overlay()
			.showText(90)
			.text("Reverse turns the output slowly the other way.")
			.pointAt(util.vector()
				.topOf(4, 1, 2))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.showSection(lever, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.setKineticSpeed(output, 5);
		scene.world()
			.toggleRedstonePower(lever);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(2, 1, 3));
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A redstone signal picks the gear instead of the panel: strength 1 is neutral, 2 to 14 are first to thirteenth, and 15 is reverse. Shifting briefly cuts the output off.")
			.pointAt(util.vector()
				.topOf(2, 1, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Past reverse on the panel is Auto. The Transmission watches the engine behind it and holds the tallest gear it can pull without overloading: it climbs a gear at a time while there is room, and drops straight to a gear that fits when the load gets too heavy.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void sump(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("sump", "An Oil Sump");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 3, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 3, 3, 2, 4, 3);
		Selection sump = util.select()
			.position(3, 1, 3);
		Selection plumbing = util.select()
			.fromTo(4, 1, 3, 5, 1, 3);
		BlockPos sumpPos = util.grid()
			.at(3, 1, 3);
		BlockPos tank = util.grid()
			.at(5, 1, 3);
		scene.world()
			.modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 4000), FluidAction.EXECUTE));
		scene.idle(10);

		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Engine oil lubricates the whole engine, not one cylinder, so it needs only one place to come in. A head's faces are better spent on intake, exhaust and fuel.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 4, 3), Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(sump, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("An Oil Sump set against any crankshaft block or cylinder head is the engine's oil pan. It holds a litre of oil, and the engine draws on it as it turns.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(sumpPos, Direction.UP), Pointing.DOWN, 50)
			.rightClick()
			.withItem(new ItemStack(CEFluids.ENGINE_OIL.getBucket()
				.orElseThrow()));
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Fill it by hand with a bucket of Engine Oil. The film builds within seconds and friction drops.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(plumbing, Direction.WEST);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Or pipe oil into any of its faces from a tank. Pipes carry one fluid at a time, so a sump takes the oil line and the head keeps its fuel line.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.UP))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(90)
			.text("Cylinder heads still take oil too, and the sump is drawn on first. Goggles on the sump show what is left.")
			.pointAt(util.vector()
				.topOf(sumpPos))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Oil goes off with use, and a spent charge drags a little more. Scroll the dial on the sump to set an Oil Exchange: spent oil is pushed out as Used Oil while fresh oil piped in takes its place.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(140)
			.colored(PonderPalette.RED)
			.text("Used Oil has to be piped away or the exchange stops. Burn it as a poor fuel, refine it back into oil, coke it into charcoal, or let it go. An Oil Filter, fitted by right-click, slows the ageing and cleans fuel out of the oil.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("The same sump takes coolant, from any face. Water works; Engine Coolant, made from water and glycerin, works better. It is spent carrying heat away, so no radiator is needed. A cool engine breathes better. A cold one drags until it warms up. Nothing ever overheats or wears out.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.UP))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.colored(PonderPalette.WHITE)
			.text("It also takes a pilot fuel: pipe diesel into it and a compression engine with no spark plug injects a small pilot shot that lights gasoline or ethanol, a dual-fuel engine.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(160);
		scene.markAsFinished();
	}

	public static void rotor(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("rotor", "A Rotary Engine");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection line = util.select()
			.fromTo(1, 2, 3, 4, 2, 3);
		Selection ports = util.select()
			.position(2, 2, 2)
			.add(util.select()
				.position(2, 3, 3));
		Selection secondPorts = util.select()
			.position(3, 2, 2)
			.add(util.select()
				.position(3, 3, 3));
		Selection second = util.select()
			.position(3, 2, 3);
		BlockPos housing = util.grid()
			.at(2, 2, 3);
		BlockPos secondHousing = util.grid()
			.at(3, 2, 3);
		scene.idle(10);

		scene.world()
			.showSection(line, Direction.DOWN);
		scene.world()
			.setKineticSpeed(line, 24);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A Rotor Housing is a link of the crankshaft that is its own cylinder and head. Inside, a triangular rotor rides on the shaft's eccentric and turns at a third of the shaft's speed.")
			.pointAt(util.vector()
				.blockSurface(housing, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.showSection(ports, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("There are no valves and no camshaft. The intake and exhaust are ports in the housing wall, which the rotor opens and shuts as it sweeps past. Pipe it like a cylinder head.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 2, 2), Direction.WEST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Each of the rotor's three faces is a chamber that goes round all four strokes in three turns of the shaft. One of them fires every turn, as often as two pistons.")
			.pointAt(util.vector()
				.centerOf(housing))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(housing, Direction.SOUTH), Pointing.RIGHT, 50)
			.rightClick()
			.withItem(CEItems.ROTOR.asStack());
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Right-click to fit the rotor, a Fuel Injector and a Spark Plug. The long, thin chamber cannot light a charge by squeezing it, so a rotor always needs a plug and a battery.")
			.pointAt(util.vector()
				.blockSurface(housing, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.replaceBlocks(second, CEBlocks.ROTOR_HOUSING.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Axis.X)
				.setValue(RotorHousingBlock.HAS_ROTOR, true)
				.setValue(RotorHousingBlock.HAS_INJECTOR, true)
				.setValue(RotorHousingBlock.HAS_SPARK_PLUG, true), true);
		scene.world()
			.showSection(secondPorts, Direction.DOWN);
		scene.world()
			.setKineticSpeed(line, 24);
		scene.idle(20);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Housings can be stacked on one shaft, each with its own ports. Set the same way, both fire at the same moment and the engine is lumpy.")
			.pointAt(util.vector()
				.blockSurface(secondHousing, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(secondHousing, Direction.SOUTH), Pointing.RIGHT, 50)
			.rightClick()
			.withItem(CEItems.TIMING_TOOL.asStack());
		scene.idle(15);
		scene.world()
			.modifyBlockEntityNBT(second, RotorHousingBlockEntity.class, nbt -> nbt.putInt("ScrollValue", THROW_HALF_TURN));
		scene.world()
			.setKineticSpeed(line, 24);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("The Timing Tool turns each eccentric for you: two rotors half a turn apart, three a third. It sets rotors round any cylinders on the same crankshaft, and leaves the cam lobes to the cylinders.")
			.pointAt(util.vector()
				.blockSurface(secondHousing, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(100)
			.text("A rotor keeps breathing at speed where a piston chokes, so it likes to rev. It burns more fuel and oil for the power than a piston does.")
			.pointAt(util.vector()
				.topOf(housing))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void starter(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("starter", "Starting an Engine");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection engine = util.select()
			.fromTo(1, 2, 3, 3, 4, 3);
		BlockPos starter = util.grid()
			.at(1, 2, 2);
		Selection input = util.select()
			.fromTo(1, 2, 0, 1, 2, 1);
		Selection power = util.select()
			.position(0, 2, 2);

		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(util.select()
				.position(starter), Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(80)
			.attachKeyFrame()
			.text("A Starter cranks the crankshaft block it faces. It is not connected to the engine's own kinetic network.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.UP))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(input, Direction.SOUTH);
		scene.world()
			.setKineticSpeed(input, 64);
		scene.world()
			.setKineticSpeed(util.select()
				.position(starter), 64);
		scene.idle(10);
		scene.overlay()
			.showText(80)
			.text("Feed it rotation through its back from any source. It cranks the engine at the speed it is turned.")
			.pointAt(util.vector()
				.topOf(1, 2, 1))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(power, Direction.DOWN);
		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 64);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(0, 2, 2));
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("It engages while powered by redstone. Turning a dead engine over takes effort, which appears as stress on the starter's input.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.WEST))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Once the engine fires and runs faster than the starter, a one-way clutch lets go. Remove the signal and it carries on by itself.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(80)
			.text("A Hand Crank on the crankshaft also works for small engines. Bigger engines need stronger starters.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(90);
		scene.markAsFinished();
	}

	public static void flywheel(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("flywheel", "Flywheels and Rough Running");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 2, 3, 1, 2);
		Selection engine = util.select()
			.fromTo(2, 2, 2, 2, 3, 2)
			.add(util.select()
				.fromTo(1, 4, 2, 3, 4, 2));
		Selection flywheel = util.select()
			.position(0, 1, 2);
		BlockPos flywheelPos = util.grid()
			.at(0, 1, 2);

		setThrow(scene, util.select()
			.position(2, 1, 2), 0);
		setLobe(scene, util.select()
			.position(2, 4, 2), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 48);
		scene.idle(20);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A power stroke pushes the crank hard, and the strokes between only drag. The speed surges with every cycle, and a Create network wants a steady torque, not pulses.")
			.pointAt(util.vector()
				.topOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("So a rough engine is rated for less. The more it surges, the more of its torque goes unused: up to two fifths of it. Goggles on the crankshaft show the cost.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 1, 2), Direction.NORTH))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(flywheel, Direction.EAST);
		scene.world()
			.setKineticSpeed(flywheel, 48);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Cast Flywheel on the crankshaft's shaft line is cheap rotating mass. Each stroke then changes the speed less, and the rating comes back. A Create Flywheel does the same, a little heavier.")
			.pointAt(util.vector()
				.topOf(flywheelPos))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(110)
			.text("Cylinders firing evenly smooth an engine too, and a faster engine surges less. Mass costs spin-up and starter effort, so it matters most for small, slow engines.")
			.pointAt(util.vector()
				.topOf(2, 3, 2))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void bankBuilder(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("bank_builder", "Laying Out a Bank");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 3, 3, 5, 3, 3);
		Selection up = util.select()
			.fromTo(2, 4, 3, 2, 5, 3)
			.add(util.select()
				.fromTo(4, 4, 3, 4, 5, 3));
		Selection down = util.select()
			.fromTo(2, 1, 3, 2, 2, 3)
			.add(util.select()
				.fromTo(4, 1, 3, 4, 2, 3));
		Selection vee = util.select()
			.fromTo(2, 3, 1, 2, 3, 2)
			.add(util.select()
				.fromTo(4, 3, 1, 4, 3, 2));
		BlockPos throwPos = util.grid()
			.at(2, 3, 3);

		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Every cylinder wants a block, a head, a piston, a rod and an injector. The Bank Builder places a whole bank in one click.")
			.pointAt(util.vector()
				.topOf(throwPos))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(throwPos, Direction.UP), Pointing.DOWN, 50)
			.rightClick()
			.withItem(new ItemStack(CEItems.BANK_BUILDER.get()));
		scene.idle(15);
		scene.world()
			.showSection(up, Direction.DOWN);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Use it on a throw: the cylinder and head go on the face you click, on every throw of the crankshaft. It takes blocks, pistons, rods and injectors from your pockets (spark plugs stay yours).")
			.pointAt(util.vector()
				.topOf(2, 5, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Sneak-use changes the layout, and then between standard and precision parts, which it also swaps in for the standard ones on the throws. Flat builds the opposite face too, for a boxer.")
			.pointAt(util.vector()
				.blockSurface(throwPos, Direction.DOWN))
			.placeNearTarget();
		scene.idle(20);
		scene.world()
			.showSection(down, Direction.UP);
		scene.idle(90);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.GREEN)
			.text("Vee builds the face a quarter turn round instead, for a 90 degree V. Then add pipes, cams and fuel, and let the Timing Tool set the firing order.")
			.pointAt(util.vector()
				.blockSurface(throwPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(20);
		scene.world()
			.showSection(vee, Direction.SOUTH);
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void dyno(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("dyno", "The Dynamometer");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 3, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 3, 3, 2, 4, 3);
		Selection dyno = util.select()
			.position(3, 1, 3);
		BlockPos dynoPos = util.grid()
			.at(3, 1, 3);

		setThrow(scene, util.select()
			.position(2, 2, 3), 0);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 48);
		scene.idle(20);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("How much can this engine carry, and at what speed? The Dynamometer answers.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(dyno, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Set it against any crankshaft block, cylinder or head. Goggles on it draw the engine's torque and power at each speed, with the peaks and the powerband.")
			.pointAt(util.vector()
				.blockSurface(dynoPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.BLUE)
			.text("It shows what the engine is rated for: after its fuel, mixture, boost and how roughly it runs. Change a part and watch the curve move.")
			.pointAt(util.vector()
				.blockSurface(dynoPos, Direction.UP))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.colored(PonderPalette.GREEN)
			.text("It gives a redstone signal that rises as the engine's speed climbs to the speed of its peak power: a shift light for a transmission.")
			.pointAt(util.vector()
				.blockSurface(dynoPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void turboTuning(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("turbo_tuning", "Vanes and Anti-Lag");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam);
		Selection turbo = util.select()
			.position(1, 3, 3);
		Selection others = util.select()
			.position(0, 3, 3)
			.add(util.select()
				.position(1, 3, 4))
			.add(util.select()
				.fromTo(1, 3, 2, 2, 3, 2));
		BlockPos turboPos = util.grid()
			.at(1, 3, 3);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(others, Direction.EAST);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.world()
			.setKineticSpeed(cam, 16);
		scene.idle(15);
		scene.world()
			.showSection(turbo, Direction.EAST);
		scene.world()
			.modifyBlockEntityNBT(turbo, TurbochargerBlockEntity.class,
				nbt -> nbt.put("Status", new TurboStatus(0.5f, 0.3f, 0, 1.2f, 0.3f, true, true, 0.8f, false).write()));
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A Variable-Geometry Turbocharger has a ring of vanes before its turbine. At low revs the exhaust is thin, so the vanes close: the gas speeds up onto the wheel and it spools sooner.")
			.pointAt(util.vector()
				.topOf(turboPos))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.modifyBlockEntityNBT(turbo, TurbochargerBlockEntity.class,
				nbt -> nbt.put("Status", new TurboStatus(0.9f, 0.8f, 0, 1.3f, 0.8f, true, true, 0.05f, false).write()));
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.world()
			.setKineticSpeed(cam, 48);
		scene.overlay()
			.showText(110)
			.colored(PonderPalette.GREEN)
			.text("At full flow they open, leaving the exhaust a freer path than a fixed turbine would, so less is lost to back-pressure.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.modifyBlockEntityNBT(turbo, TurbochargerBlockEntity.class,
				nbt -> nbt.put("Status", new TurboStatus(0.6f, 0.4f, 0, 1.2f, 0.4f, true, true, 0.4f, true).write()));
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Lift off the throttle and the boost normally dies. With anti-lag set on either turbo, it burns fuel in the exhaust ahead of the turbine while the engine is off the throttle, so the boost is waiting when you open it again.")
			.pointAt(util.vector()
				.topOf(turboPos))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(100)
			.text("It costs fuel in proportion to its level, and does nothing on the throttle. Set it from the value box beside the boost limit.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void heatExchanger(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("heat_exchanger", "Cooking Fuel on Exhaust Heat");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(cam)
			.add(util.select()
				.position(2, 3, 2));
		Selection outlet = util.select()
			.position(2, 3, 5);
		Selection body = util.select()
			.position(2, 3, 4);
		Selection basin = util.select()
			.position(2, 4, 4);
		Selection mixer = util.select()
			.fromTo(2, 6, 4, 3, 6, 4);
		BlockPos exchanger = util.grid()
			.at(2, 3, 4);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(outlet, Direction.NORTH);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.world()
			.showSection(body, Direction.NORTH);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("An Exhaust Heat Exchanger is a length of exhaust pipe that takes the heat out of the gas going through it.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(basin, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Basin set on top of it reads that heat as if it stood on a blaze burner, so a running engine can cook its own fuel.")
			.pointAt(util.vector()
				.topOf(2, 4, 4))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.modifyBlock(exchanger, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.KINDLED), false);
		scene.world()
			.showSection(mixer, Direction.DOWN);
		scene.world()
			.setKineticSpeed(mixer, 32);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("From about two cylinders running at speed it counts as heated: enough for the diesel, ethanol and biodiesel recipes.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.modifyBlock(exchanger, state -> state.setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.SEETHING), false);
		scene.idle(10);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("From about seven it counts as superheated, enough to crack diesel into gasoline without a blaze burner.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(140)
			.colored(PonderPalette.BLUE)
			.text("It warms and cools over about a second and costs the engine nothing. Its heat also comes out as a redstone signal, and goggles read it in heat per second. The server config can switch heat recovery off.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(150);
		scene.markAsFinished();
	}

	public static void fuels(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("fuels", "Refining Fuel");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection pressed = util.select()
			.fromTo(1, 1, 3, 2, 4, 3);
		Selection heated = util.select()
			.fromTo(3, 1, 3, 4, 4, 3);
		Selection superheated = util.select()
			.fromTo(5, 1, 3, 5, 4, 3);
		Selection drive = util.select()
			.fromTo(1, 4, 3, 5, 4, 3);

		scene.world()
			.showSection(pressed, Direction.DOWN);
		scene.world()
			.setKineticSpeed(drive, 32);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Seeds pressed in a Basin make Plant Oil, four seeds to 50 mB. Sugar cane, beetroot or potatoes pressed with water make Sugar Mash.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.showSection(heated, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Over a blaze burner, or an Exhaust Heat Exchanger, a heated Basin cooks Plant Oil with coal into Diesel, and Sugar Mash with a brown mushroom into Ethanol.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(140)
			.text("Plant Oil, Ethanol and bone meal make more Diesel, with Glycerin on the side. Plant Oil with Glycerin makes a better Engine Oil.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(150);

		scene.world()
			.showSection(superheated, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Superheated, Diesel with blaze powder cracks into Gasoline.")
			.pointAt(util.vector()
				.topOf(5, 2, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(150)
			.colored(PonderPalette.BLUE)
			.text("Diesel burns by compression alone. Gasoline and Ethanol need a spark plug, or a diesel pilot shot. Ethanol carries less energy than gasoline, but a hot, boosted charge will not light it early.")
			.pointAt(util.vector()
				.topOf(5, 2, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Water with Glycerin makes Engine Coolant. Used Oil from an oil exchange heated with charcoal makes Engine Oil again, or coked on its own it makes charcoal, which goes back into Diesel. Nothing is wasted.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(160);
		scene.markAsFinished();
	}
}
