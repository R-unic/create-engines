package com.createengines.ponder;

import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.cylinder.CylinderBlock;
import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
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
			.showText(70)
			.attachKeyFrame()
			.text("A crankshaft is a line of Crankshaft and Crank Throw blocks on one axis. It turns like any Create shaft.")
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
			.text("A throw's angle can be changed with its value panel. Here the second throw is turned 180°, so its piston moves opposite the first.")
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
			.text("The head's other faces are its connections: an intake, an exhaust, and a Cam Lobe to work the valves and injector.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.WEST))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("Fuel is piped into any face of a head. Heads that touch share their fuel, so one pipe can feed a whole bank.")
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
			.showText(80)
			.attachKeyFrame()
			.text("A Cam Lobe touching a cylinder head opens its valves and fires its injector as the camshaft turns.")
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
			.text("Two angles 180° apart both work: each picks which crank revolution the cylinder fires on. This is how firing order is set.")
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
		Selection battery = util.select()
			.position(0, 1, 3);
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
			.showSection(battery, Direction.EAST);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Plugs fire from a Battery touching the crankshaft or a cylinder head. Each spark uses a little charge, and a flat battery gives no spark.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(0, 1, 3), Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.setKineticSpeed(crank.add(battery), 48);
		scene.overlay()
			.showText(100)
			.text("A battery charges while its shaft turns, for a little stress. On the crank's own shaft line, cranking charges it and the running engine keeps it topped up.")
			.pointAt(util.vector()
				.topOf(0, 1, 3))
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
			.text("Pipe Engine Oil into any face of a Cylinder Head. It has a second tank for oil, apart from the fuel. Heads that touch share it.")
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
}
