package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.content.rotor.RotorHousingBlock;
import com.createengines.content.rotor.RotorHousingBlockEntity;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEItems;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Ponder scenes for the governor, transmission, flywheels, coupled cranks and rotary engine. Text here is the English source; datagen writes it to the lang file. */
public final class DrivelineScenes {

	private DrivelineScenes() {
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
			.text("Its Governed Speed is set with the value panel to any exact speed up to 256 RPM, or in steps of 5 up to 1000. A spark engine is governed on its throttle instead, because a charge that is too lean will not light.")
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
			.text("A redstone signal picks the gear instead of the panel: strength 1 to 13 is that gear, 14 is reverse, and 15 is Auto. Shifting briefly cuts the output off.")
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

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A second panel on the face sets how eager Auto is. Normal is the usual behaviour. Economy climbs early and sits in a tall gear, working the engine harder at low revs. Sport drops early and holds a low gear, keeping the revs up with torque in hand.")
			.pointAt(util.vector()
				.blockSurface(boxPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(140);
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

		scene.world()
			.setBlock(flywheelPos, CEBlocks.DUAL_MASS_FLYWHEEL.getDefaultState()
				.setValue(BlockStateProperties.AXIS, Direction.Axis.X), false);
		scene.world()
			.setKineticSpeed(flywheel, 48);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Dual-Mass Flywheel is two masses on a spring pack. The light one is bolted to the crank, so the engine still revs quickly. The heavy one sits behind the springs and smooths what the network sees.")
			.pointAt(util.vector()
				.topOf(flywheelPos))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.RED)
			.text("The springs only isolate above their resonance, about 48 RPM. Below it the part is no better than its light mass alone, so a big Cast Flywheel still suits a slow, heavy engine. It is never worse than its light mass, and it costs a precision mechanism.")
			.pointAt(util.vector()
				.topOf(flywheelPos))
			.placeNearTarget();
		scene.idle(140);
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
}
