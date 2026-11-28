package com.createengines.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

/**
 * An engine on a moving structure (Create Aeronautics, through Sable). The page is for builds that use it and does not need the
 * mod: it says what changes for an engine that is carried, and the engine in it is only lifted to show the climb.
 */
public final class AeronauticsScenes {

	private AeronauticsScenes() {
	}

	public static void movingEngine(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("moving_engine", "Engines on Moving Structures");
		scene.configureBasePlate(0, 0, 7);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection cam = util.select()
			.fromTo(1, 4, 3, 3, 4, 3);
		Selection whole = util.select()
			.fromTo(1, 1, 3, 3, 4, 3)
			.add(util.select()
				.position(2, 3, 2))
			.add(util.select()
				.position(2, 3, 4))
			.add(util.select()
				.position(2, 3, 5));

		SceneHelpers.setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		SceneHelpers.setLobe(scene, util.select()
			.position(2, 4, 3), SceneHelpers.LOBE_FOR_UPRIGHT_CYLINDER);
		ElementLink<WorldSectionElement> engine = scene.world()
			.showIndependentSection(whole, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 64);
		scene.world()
			.setKineticSpeed(cam, 32);
		scene.idle(20);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Create Aeronautics carries blocks as a moving structure. An engine built into one is still an engine: it is found and run as it is on the ground, and gives its stress to the shafts it turns.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.moveSection(engine, util.vector()
				.of(0, 3, 0), 60);
		scene.idle(20);
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("The air thins as it climbs. Above height 128 an engine breathes less and loses power; a turbocharger or a supercharger makes up for it. The engine reads its height in the world, not where its blocks are stored.")
			.pointAt(util.vector()
				.centerOf(2, 6, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Weight counts. Cylinders, the crankshaft and turbochargers are heavy, a rotor housing is lighter for its power, manifolds and intakes weigh next to nothing, and the cast flywheel is the heaviest thing on the crank.")
			.pointAt(util.vector()
				.centerOf(2, 5, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(140)
			.text("What the engine does to the world happens where the structure really is: its exhaust and smoke, rain steaming off a hot head, a battery's cold, and gas released through the exhaust by Pollution of the Realms.")
			.pointAt(util.vector()
				.centerOf(2, 6, 5))
			.placeNearTarget();
		scene.idle(150);
		scene.markAsFinished();
	}
}
