package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.registry.CEItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** Ponder scenes for tools, upgrades and readouts. Text here is the English source; datagen writes it to the lang file. */
public final class ToolScenes {

	private ToolScenes() {
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
			.text("Use it on a throw: the cylinder and head go on the face you click, on every throw of the crankshaft. It takes blocks, pistons, rods and injectors from your pockets, and spark plugs too if you carry any: a diesel needs none.")
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
			.text("Vee builds the face a quarter turn round instead, for a 90 degree V. Carry a Camshaft and Cam Lobes and it lays the cam line too: beside each cylinder block, or in the valley of a vee, where one lobe times both banks. Gear it to half the crank's speed, add pipes and fuel, and let the Timing Tool set the firing order.")
			.pointAt(util.vector()
				.blockSurface(throwPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(20);
		scene.world()
			.showSection(vee, Direction.SOUTH);
		scene.idle(120);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.text("W builds three banks, the face you click and both beside it, as here, with a cam line in each valley. X builds all four. Not every W fires evenly: eight throws of three cannot, but leave a bank off three throws of nine and they can. Six throws of an X can.")
			.pointAt(util.vector()
				.blockSurface(throwPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);
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

	public static void makerPlate(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("maker_plate", "The Maker's Plate");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 3, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 3, 3, 2, 4, 3);
		Selection plate = util.select()
			.position(3, 2, 4);
		BlockPos platePos = util.grid()
			.at(3, 2, 4);

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
			.text("Every engine builder puts a name on their work. The Maker's Plate is that nameplate.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(plate, Direction.NORTH);
		scene.world()
			.modifyBlockEntity(platePos, MakerPlateBlockEntity.class, be -> be.setNameAndBuilder("Old Faithful", "You"));
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Screw it to any solid part of the engine. Rename it on an anvil first and the name is engraved on the plate, with the name of whoever placed it.")
			.pointAt(util.vector()
				.blockSurface(platePos, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(platePos, Direction.SOUTH), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(50);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Use it with an empty hand once the engine's curve has been measured, as a Dynamometer does, and it stamps the engine's peak power and peak torque. Goggles read them back.")
			.pointAt(util.vector()
				.blockSurface(platePos, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(100)
			.colored(PonderPalette.GREEN)
			.text("It is for looks and bragging: it changes nothing about how the engine runs.")
			.pointAt(util.vector()
				.blockSurface(platePos, Direction.UP))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void blackBox(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("black_box", "The Black Box");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 3, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 3, 3, 2, 4, 3);
		Selection box = util.select()
			.position(3, 1, 3);
		BlockPos boxPos = util.grid()
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
			.text("An engine that stalls rarely says why, and by the time you look it is cold. The Black Box remembers.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(box, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Set it against any block of a built engine: a crankshaft, a cylinder or a head. Once a second it notes the engine's speed, its load and how warm it is, and keeps the last ten minutes.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.BLUE)
			.text("Goggles on it read the tape back as rows of bars, oldest first, with a mark for each state the engine was in: stalled, cranking, coasting, running or overloaded.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.UP))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.setKineticSpeed(crank, 0);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.colored(PonderPalette.RED)
			.text("Every time the engine changes state it logs the change, and says what the figures point to: no fuel reaching it, a load more than it could pull, or no oil.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(90)
			.colored(PonderPalette.GREEN)
			.text("The tape is kept in the block, so it is still there after the world is saved and loaded.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("Turn the Replay dial on its side to step back along the tape. Goggles then show what the engine was doing at that moment and what it blamed, and the moment holds still while the tape runs on.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void engineReadouts(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("engine_readouts", "Reading an Engine from Afar");
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
		BlockPos crankPos = util.grid()
			.at(3, 2, 3);
		BlockPos sumpPos = util.grid()
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
			.text("Goggles show an engine's figures, but only from beside it. A Display Link can carry them anywhere.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(crankPos, Direction.SOUTH), Pointing.RIGHT, 50)
			.rightClick()
			.withItem(new ItemStack(AllBlocks.DISPLAY_LINK.get()));
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Set a Display Link against any crankshaft block of an engine, and give it a display board or nixie tubes. Engine Status lists what the engine is doing, its speed, load, temperature and oil, a line each.")
			.pointAt(util.vector()
				.blockSurface(crankPos, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(190)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Engine Speed gives the RPM for a nixie tube. Engine Load and Oil Condition each give a percentage or a progress bar, and Temperature gives degrees or a bar that is full when the block is warm. Load is how much of its rating the network asks of it. Engine Boost, Engine Rated Torque and Battery Charge are there too, Engine Fault names what is wrong with it, and against a Transmission, Transmission Gear gives the gear.")
			.pointAt(util.vector()
				.blockSurface(crankPos, Direction.UP))
			.placeNearTarget();
		scene.idle(200);

		scene.world()
			.showSection(sump, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Against an oil sump, Oil Sump Levels lists what is in each of its four tanks. A comparator on the sump reads one of them too, to switch a pump or sound an alarm when the oil runs low.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}
}
