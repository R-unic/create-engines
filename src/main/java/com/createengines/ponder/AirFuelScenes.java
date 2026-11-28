package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Ponder scenes for air, fuel, spark and the fuels. Text here is the English source; datagen writes it to the lang file. */
public final class AirFuelScenes {

	private AirFuelScenes() {
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
			.showText(150)
			.text("Exhaust Manifolds do the same for an Exhaust Outlet, as pipes can bend and join like a real exhaust. One opening for each head gives nearly all the air it can breathe. An opening shared by two heads gives about two thirds, a second opening on a head adds little, and openings touching each other count for half.")
			.pointAt(util.vector()
				.topOf(3, 4, 4))
			.placeNearTarget();
		scene.idle(160);
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
			.showText(170)
			.attachKeyFrame()
			.text("A battery makes no charge itself. An Alternator charges the batteries touching it, and those of the engine it touches, while its shaft turns, for a little stress. Its goggles say how fast it is charging and what share of the engine's rating it takes; fit several and each reads out its share of the charge made.")
			.pointAt(util.vector()
				.topOf(0, 1, 3))
			.placeNearTarget();
		scene.idle(180);

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
			.text("Batteries that touch are one bank: capacity adds up, and anything touching one of them reaches all of them. The lamps on a lid show its charge, and a Comparator reads the whole bank.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(170)
			.colored(PonderPalette.BLUE)
			.text("Cold costs charge. In a cold biome a battery gives only part of what it holds, and a cold engine makes each spark cost more. A heat source beside it, like a Blaze Burner, warms it. A starter draws on the batteries as it cranks a spark engine, less as they run low and none from an empty bank. With rotation on its input it still turns at full speed; with none, the batteries alone turn it, so an empty bank leaves it stopped. Keep them charged or crank by hand.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(180);

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
			.text("Its Mixture Target, set with the value panel, is how lean or rich it holds the shot: 80%% to 125%% of balanced.")
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
			.showText(110)
			.attachKeyFrame()
			.text("A Throttle Body is a length of intake pipe with a valve in it. Close it and every cylinder behind it draws less air.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.text("It replaces a piece of the intake line between the Air Intake and the cylinders, and air runs through it like any manifold piece. Only the cylinders behind it feel the valve.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(throttle, Direction.WEST), Pointing.RIGHT, 40)
			.rightClick();
		scene.idle(10);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its Idle Stop, set with the value panel, is how far open the valve rests with no signal: 5%% to 100%% in steps of 5%%, and 20%% to begin with. Set it higher for an engine that should idle strongly; set it low for one that should barely tick over.")
			.pointAt(util.vector()
				.blockSurface(throttle, Direction.WEST))
			.placeNearTarget();
		scene.world()
			.setKineticSpeed(crank, 24);
		scene.world()
			.setKineticSpeed(cam, 12);
		scene.idle(140);

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

		scene.overlay()
			.showText(130)
			.text("The opening rises in a straight line with signal strength. With a 20%% idle stop, strength 0 is 20%%, strength 8 is about 63%% and strength 15 is 100%%. Engineer's Goggles on the valve read its opening right now.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.BLUE)
			.text("A second dial, the Wide-Open Bore, sets how far full signal opens it: 100%% is the plain intake, and up to 150%% is a bigger bore that lets a fast engine breathe more than the intake alone gives. It does nothing with no signal.")
			.pointAt(util.vector()
				.topOf(3, 3, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.toggleRedstonePower(pedal);
		scene.world()
			.setKineticSpeed(crank, 24);
		scene.world()
			.setKineticSpeed(cam, 12);
		scene.overlay()
			.showText(130)
			.text("Less air means less power: a throttled engine runs slower and has less torque to spare. Nearly shut, the piston spends more dragging air through the valve than it earns. Air gain flattens as the valve opens, so the last stretch of travel adds little and the first stretch matters most.")
			.pointAt(util.vector()
				.topOf(2, 2, 3))
			.placeNearTarget();
		scene.idle(140);

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
			.showText(120)
			.text("Throttle bodies in one intake line act together: the most closed one governs every cylinder behind it. A bore past 100%% only helps when every body in the line is set that wide.")
			.pointAt(util.vector()
				.topOf(throttle))
			.placeNearTarget();
		scene.idle(130);
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

	public static void fuels(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("fuels", "Making Fuel in a Basin");
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
			.text("A Basin is the simple way to make fuel; a Refining Column makes the same fuels faster and gives more from the feed. Seeds or saplings pressed in a Basin make Plant Oil, six seeds to 100 mB or two saplings to 50 mB. Sugar cane, beetroot or potatoes pressed with water make Sugar Mash.")
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
			.text("Over a blaze burner, or an Exhaust Heat Exchanger, a heated Basin cooks Plant Oil with coal into Diesel, and Sugar Mash with an apple or any mushroom into Ethanol.")
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
			.text("Heated too, Diesel with blaze powder cracks into Gasoline, at no more than the cost of making Diesel.")
			.pointAt(util.vector()
				.topOf(5, 2, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(150)
			.colored(PonderPalette.RED)
			.text("Superheated, as over a big engine's Exhaust Heat Exchanger, Sugar Mash with an apple or mushroom and a sugar distils 100 mB of Ethanol instead of 50. Create runs the recipe that asks for the most items, so the extra sugar is what selects it.")
			.pointAt(util.vector()
				.topOf(5, 2, 3))
			.placeNearTarget();
		scene.idle(160);

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
			.text("Water with Glycerin makes Engine Coolant. Used Oil from an oil exchange heated with charcoal makes Engine Oil again, 300 mB instead of 250 if superheated with a clay ball as well, or coked on its own it makes charcoal, which goes back into Diesel. Nothing is wasted.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.colored(PonderPalette.BLUE)
			.text("Heated, Diesel with glowstone dust makes Kerosene instead of Gasoline. A refining column goes the way of the catalyst fed to its Reboiler. Kerosene needs a spark plug, carries more energy than Diesel and a little less than Gasoline, and holds out against a hot, boosted charge better than Gasoline does.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Superheated, logs and water make Methanol: 60 mB to the log in a basin, a little more in a refining column with a log fed to a Reboiler. It needs a spark plug and carries the least energy of any fuel, so an injector must give it a lot more flow. In return it cools the charge most, leaves no soot, and a hot, boosted charge will hardly light it early, even past what Ethanol takes.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(160);
		scene.markAsFinished();
	}
}
