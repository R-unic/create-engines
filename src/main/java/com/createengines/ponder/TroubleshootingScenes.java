package com.createengines.ponder;

import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The troubleshooting pages: what to look at when an engine will not start, and when it runs badly. Both are told on the
 * spark-ignition structure, a single cylinder on a crank with a battery, and they point at the parts a fault lives in. The
 * words match what goggles say, from the same causes.
 */
public final class TroubleshootingScenes {

	/** Lobe angle step that times a cylinder bored straight up from a throw at 0° on an X-axis crank. */
	private static final int LOBE_FOR_UPRIGHT_CYLINDER = 18;

	private TroubleshootingScenes() {
	}

	/** Lays out the single-cylinder engine: its crank, the cylinder, head and manifolds, and the cam line. */
	private static Selection layOut(CreateSceneBuilder scene, SceneBuildingUtil util) {
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);
		scene.world()
			.modifyBlockEntityNBT(util.select()
				.position(2, 1, 3), CrankshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", 0));
		scene.world()
			.modifyBlockEntityNBT(util.select()
				.position(2, 4, 3), CamshaftBlockEntity.class, nbt -> nbt.putInt("ScrollValue", LOBE_FOR_UPRIGHT_CYLINDER));
		Selection engine = util.select()
			.fromTo(1, 1, 3, 3, 1, 3)
			.add(util.select()
				.fromTo(2, 2, 3, 2, 3, 3))
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 4))
			.add(util.select()
				.fromTo(1, 4, 3, 3, 4, 3));
		scene.world()
			.showSection(engine, Direction.DOWN);
		return engine;
	}

	public static void whyWontItStart(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("troubleshooting_start", "Why Won't It Start?");
		layOut(scene, util);
		BlockPos cylinder = util.grid()
			.at(2, 2, 3);
		BlockPos head = util.grid()
			.at(2, 3, 3);
		Selection battery = util.select()
			.position(1, 2, 3);
		scene.world()
			.showSection(battery, Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("An engine that will not start is missing one of four things: air, fuel, a spark or something turning it. Look at a Cylinder through Engineer's Goggles first. It says what stopped its last cycle, and the crankshaft says what is wrong with the engine as a whole.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("No air: the intake has no open Air Intake on its end, the intake is covered or underwater, or the cylinder has no intake at all. Goggles say \"Air cannot get in\".")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 2), Direction.NORTH))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("No fuel: the head has no Fuel Injector, the fuel tank or pipe is empty, or a redstone signal on the camshaft has shut the rack. Goggles say \"No fuel is reaching a cylinder\".")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("No exhaust: burned gas must have somewhere to go. An Exhaust Outlet that is covered or underwater stops the cylinder as surely as no pipe at all.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 4), Direction.SOUTH))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.hideSection(battery, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("No spark: gasoline and ethanol will not light by compression alone. A Spark Plug needs a charged Battery touching the engine. Goggles say \"The spark plug is not firing\".")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(110);
		scene.world()
			.showSection(battery, Direction.DOWN);
		scene.idle(15);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Nothing turning it: a dead engine has to be cranked, by a Starter or a Hand Crank on its crankshaft, fast enough for the charge to heat and the cam to open the valves. A pilot shot of diesel stands in for a plug.")
			.pointAt(util.vector()
				.topOf(3, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Cold: a cold block drags and burns weakly, ethanol and pressed oil worst of all, gasoline best. Give it a little throttle and a minute, or park a Blaze Burner against its Heat Exchanger so it is already warm when it is started.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(100)
			.text("A missing piston, rod or head, or a cylinder not on a crank throw, takes that cylinder out entirely. Goggles on the cylinder list exactly which part.")
			.pointAt(util.vector()
				.blockSurface(cylinder, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}

	public static void runningBadly(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("troubleshooting_running", "When an Engine Runs Badly");
		layOut(scene, util);
		BlockPos cylinder = util.grid()
			.at(2, 2, 3);
		BlockPos head = util.grid()
			.at(2, 3, 3);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("The crankshaft's goggles name the engine Healthy, Running rough or Failing, and under it the one cause most responsible. Fix that first, then look again: the next cause shows up.")
			.pointAt(util.vector()
				.topOf(3, 1, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Misfires: a spark only lights a mixture that is neither too lean nor too rich. Goggles on the cylinder show its mixture and suggest a metering. A plug caked with soot from running rich misses until it burns clean.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Smoke tells the same story. Black means rich, pale grey means lean, and a blue cast means fuel has thinned the oil. A thick white cloud from a cold engine is only steam, and clears as the block warms.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 4), Direction.SOUTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.text("Surging: cylinders that fire in bunches, or only a few at a time, make the speed ripple and cost rating. Add cylinders, even out the firing order with the Timing Tool, or fit a Flywheel.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.WHITE)
			.text("Dry bearings squeal and drag, and an oil pan that is empty is the first thing to check. Spent oil drags a little more, and an Oil Filter and an oil exchange keep it fresh.")
			.pointAt(util.vector()
				.blockSurface(cylinder, Direction.WEST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Overloaded: if the network asks for more than the engine can give at its speed, it slows and eventually stalls. Compare the engine's Load with its Available Torque in goggles, and read its curve on a Dynamometer.")
			.pointAt(util.vector()
				.topOf(1, 1, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("After a stop the block cools: the metal ticks, the head shimmers and an exhaust turbine glows red until it has given up its heat. Goggles call it Cooling down. A hot engine is never harmed, but a restart before it cools is easier.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}
}
