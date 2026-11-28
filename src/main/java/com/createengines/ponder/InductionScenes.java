package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.content.turbo.TurbochargerBlockEntity;
import com.createengines.engine.TurboStatus;

import com.simibubi.create.AllBlocks;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Ponder scenes for forced induction and heat. Text here is the English source; datagen writes it to the lang file. */
public final class InductionScenes {

	private InductionScenes() {
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
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Its wastegate vents exhaust round the turbine to hold the boost at the limit set on its value panel. Its goggles show how hard it is working as a share of that limit, gold at 95 percent and over, so the limit can be set by reading it.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(160);

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
			.showText(150)
			.text("A redstone signal on the turbocharger opens the wastegate fully and dumps the boost, like a blow-off valve. Lifting off the throttle with boost in the pipe does the same. The vented air leaves in a burst and then a thinning jet, longer the more boost there was, and the wastegate gives a small grey puff off the turbine end.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.DOWN))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Pick a Turbocharger for an engine that runs hard and steady. Its boost is free: nothing loads a shaft, it grows with the engine's own exhaust, and it is strongest in the middle of the rev range. It pays in lag, in back-pressure, and in the fuel it needs to keep spinning.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("A Supercharger is worth the same, for other jobs: boost at once, with no exhaust to wait for. See its page for when to choose one instead.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(140);
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
			.text("It needs no exhaust and has no lag. Its boost climbs with its shaft speed to the power of one and a quarter: 0.9 bar at 128 RPM, and still 40 percent of that at half the speed.")
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

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.GREEN)
			.text("A Variable-Drive Supercharger, made from a Supercharger and a Precision Mechanism, overspeeds its rotors while the shaft is slow, so it boosts sooner, takes less power to turn and leaves the air cooler.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.showSection(cooler, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Squeezing air heats it, whichever compressor does it. An Intercooler in the run, after the compressors, takes 60 percent of that heat back out.")
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

		scene.overlay()
			.showText(160)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Pick a Supercharger when the engine cannot wait for exhaust: a cold start, an engine that stalls under load, one held at low speed by a governor or throttle, or a load that swings. Its boost needs no fuel and adds no back-pressure, and it keeps its power at high revs and high boost, where a turbine chokes the exhaust.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(170);

		scene.overlay()
			.showText(190)
			.colored(PonderPalette.RED)
			.text("It pays in stress on the shaft that turns it, and it makes only as much boost as that shaft's speed allows, so gear the shaft up for a bigger engine. Its goggles show how hard it is working as a share of its boost limit, the same way a Turbocharger's do. Net of that stress it makes about what a Turbocharger does at the same limit, and a Variable-Drive Supercharger about what a Variable-Geometry Turbocharger does: choose by how the engine is used, not by which is better.")
			.pointAt(util.vector()
				.blockSurface(blowerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(200);
		scene.markAsFinished();
	}

	public static void radiator(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("radiator", "A Radiator");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3);
		BlockPos radiatorPos = util.grid()
			.at(3, 1, 2);
		Selection fan = util.select()
			.position(3, 1, 0);
		Selection basin = util.select()
			.position(3, 2, 2);
		scene.idle(10);

		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 128);
		scene.idle(15);
		scene.world()
			.showSection(util.select()
				.position(radiatorPos), Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Radiator touching a crankshaft, cylinder or cylinder head cools the whole engine. An engine can have several.")
			.pointAt(util.vector()
				.blockSurface(radiatorPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(radiatorPos, Direction.UP), Pointing.DOWN, 50)
			.rightClick()
			.withItem(new ItemStack(Items.WATER_BUCKET));
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Fill it with water or Engine Coolant, by bucket or pipe. The loop is closed: nothing is used up unless the engine gets critically hot, when it starts to boil.")
			.pointAt(util.vector()
				.blockSurface(radiatorPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.showSection(fan, Direction.SOUTH);
		scene.world()
			.setKineticSpeed(fan, 256);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("It sheds more with air going through it. Point an Encased Fan at it, up to two blocks of air away, or carry the engine on a moving Aeronautics structure.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(3, 1, 0), Direction.UP))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.showSection(basin, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A basin or a boiler on top uses the waste heat: while the radiator sheds enough, it counts as a heated source. It never superheats.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(3, 2, 2), Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);
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

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("The Variable-Drive Supercharger is the blower's version of this upgrade, from a Precision Mechanism too: it overspeeds its rotors while the shaft is slow, so it boosts sooner, takes less power to turn and runs cooler.")
			.pointAt(util.vector()
				.blockSurface(turboPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(140);
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
			.text("From about seven it counts as superheated, like a blaze burner fed blaze cake: enough to distil Ethanol twice as far and to recycle Used Oil into more Engine Oil.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(140)
			.colored(PonderPalette.BLUE)
			.text("It warms and cools over about a second and costs the engine nothing. Its heat also comes out as a redstone signal, and goggles read it in HU, heat units, per second. The server config can switch heat recovery off.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("For the most heat, add burning cylinders on the one exhaust: each running at speed on a full shot gives about 3 HU/s, and they add up along a shared manifold. An idle or unfired cylinder gives nothing.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(160);
		scene.overlay()
			.showText(150)
			.colored(PonderPalette.RED)
			.text("Use one exchanger on an exhaust for a hot basin: exchangers on the same exhaust split its heat between them. Goggles show the figure: 6 HU/s heats a basin, 20 HU/s superheats it.")
			.pointAt(util.vector()
				.topOf(exchanger))
			.placeNearTarget();
		scene.idle(160);

		scene.world()
			.hideSection(mixer, Direction.UP);
		scene.idle(15);
		scene.world()
			.replaceBlocks(basin, AllBlocks.FLUID_TANK.getDefaultState(), true);
		scene.idle(10);
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A fluid tank in its place is a boiler heated the same way: kindled is one level of boiler and seething is two. Put Create steam engines on it, and their power adds to the engine's: a combined cycle, from heat that was going up the chimney.")
			.pointAt(util.vector()
				.topOf(2, 4, 4))
			.placeNearTarget();
		scene.idle(160);
		scene.markAsFinished();
	}
}
