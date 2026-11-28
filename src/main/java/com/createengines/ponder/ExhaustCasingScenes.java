package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.client.RainCaps;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Ponder scenes for the exhaust fittings and the engine casing. Text here is the English source; datagen writes it to the lang file. */
public final class ExhaustCasingScenes {

	private ExhaustCasingScenes() {
	}

	public static void muffler(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("muffler", "Quieting the Exhaust");
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
			.add(cam)
			.add(util.select()
				.position(2, 3, 2));
		Selection pipe = util.select()
			.position(2, 3, 4);
		Selection outlet = util.select()
			.position(2, 3, 6);
		Selection muffler = util.select()
			.position(2, 3, 5);
		BlockPos mufflerPos = util.grid()
			.at(2, 3, 5);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(pipe, Direction.NORTH);
		scene.world()
			.showSection(outlet, Direction.NORTH);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Every firing is a bang, and a big engine's exhaust carries a long way.")
			.pointAt(util.vector()
				.topOf(2, 3, 6))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(muffler, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Muffler set in the exhaust run takes the bark out of it. The engine's exhaust note, its bangs and its pops all come out quieter.")
			.pointAt(util.vector()
				.topOf(mufflerPos))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.BLUE)
			.text("It costs the engine nothing: no power and no back-pressure. It changes what you hear and nothing else.")
			.pointAt(util.vector()
				.blockSurface(mufflerPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.text("It quiets the exhaust that goes through it. On a V with one bank muffled the engine is half as quiet, and a muffler the exhaust can go round does half as much. The exhaust pipes join its two ends.")
			.pointAt(util.vector()
				.blockSurface(mufflerPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(150);
		scene.markAsFinished();
	}

	public static void rainCap(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("rain_cap", "Capping an Exhaust");
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
			.add(cam)
			.add(util.select()
				.position(2, 3, 2));
		Selection pipe = util.select()
			.position(2, 3, 4);
		Selection outlet = util.select()
			.position(2, 3, 5);
		Selection cap = util.select()
			.position(2, 3, 6);
		BlockPos outletPos = util.grid()
			.at(2, 3, 5);
		BlockPos capPos = util.grid()
			.at(2, 3, 6);

		setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		setLobe(scene, util.select()
			.position(2, 4, 3), LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(pipe, Direction.NORTH);
		scene.world()
			.showSection(outlet, Direction.NORTH);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("An Exhaust Outlet is a bare pipe end, open to the weather.")
			.pointAt(util.vector()
				.blockSurface(outletPos, Direction.SOUTH))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(outletPos, Direction.SOUTH), Pointing.RIGHT, 40)
			.rightClick()
			.withItem(CEBlocks.RAIN_CAP.asStack());
		scene.idle(10);
		scene.world()
			.showSection(cap, Direction.NORTH);
		scene.idle(40);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Rain Cap goes on the open mouth of an exhaust outlet: use it on the end the exhaust comes out of. It fits there and nowhere else.")
			.pointAt(util.vector()
				.topOf(capPos));
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Its flap hops with every exhaust pulse. A slow engine clacks it once a firing; a fast one keeps it fluttering.")
			.pointAt(util.vector()
				.blockSurface(capPos, Direction.SOUTH));
		// A scene has no engine controller to pulse the flap, so it is kicked here: slowly, then quickly
		for (int i = 0; i < 4; i++) {
			scene.idle(14);
			scene.addInstruction(ponderScene -> RainCaps.pulse(ponderScene.getWorld(), capPos));
		}
		for (int i = 0; i < 12; i++) {
			scene.idle(3);
			scene.addInstruction(ponderScene -> RainCaps.pulse(ponderScene.getWorld(), capPos));
		}
		scene.idle(40);

		scene.world()
			.setKineticSpeed(crank, 0);
		scene.world()
			.setKineticSpeed(cam, 0);
		scene.overlay()
			.showText(100)
			.text("When the engine stops, the flap drops shut.")
			.pointAt(util.vector()
				.blockSurface(capPos, Direction.SOUTH));
		scene.idle(110);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("It is only for looks. The exhaust still leaves from under it, and the engine runs exactly as it did without it.")
			.pointAt(util.vector()
				.blockSurface(capPos, Direction.EAST));
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void engineCasing(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("engine_casing", "Cladding an Engine");
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
			.add(cam)
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 2))
			.add(util.select()
				.fromTo(2, 3, 4, 2, 3, 5));
		Selection cowl = util.select()
			.position(1, 2, 3)
			.add(util.select()
				.position(3, 2, 3))
			.add(util.select()
				.position(2, 2, 2))
			.add(util.select()
				.position(2, 2, 4))
			.add(util.select()
				.position(1, 3, 3))
			.add(util.select()
				.position(3, 3, 3));

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
			.showText(100)
			.attachKeyFrame()
			.text("A bank of cylinders with its pipework is a lot of loose parts to look at.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(cowl, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Engine Casing is plain riveted plate for building a cowl round an engine. It does nothing to how the engine runs, and it can be used on shafts, cogwheels, cylinders and pipes too. Casing beside casing joins into one plate, so clad it however you like.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("Leave the faces that pipes, sensors, the camshaft and an oil sump use clear: casing joins nothing, so it cannot stand in for them.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 3, 3), Direction.EAST))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}
}
