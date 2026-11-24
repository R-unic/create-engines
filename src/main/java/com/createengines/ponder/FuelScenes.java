package com.createengines.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

/** Choosing a fuel: what each fuel is for, so the choice is a reading and not a guess. Text over a running engine. */
public final class FuelScenes {

	private FuelScenes() {
	}

	public static void choosingAFuel(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("choosing_a_fuel", "Choosing a Fuel");
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

		EngineScenes.setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		EngineScenes.setLobe(scene, util.select()
			.position(2, 4, 3), EngineScenes.LOBE_FOR_UPRIGHT_CYLINDER);
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
			.showText(130)
			.attachKeyFrame()
			.text("Every fuel is a trade. Hover over a bucket or a tank of it: its tooltip says what it carries, how it is lit, how it takes the cold, how clean it burns and how a spill behaves.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Plant oil needs no refining and shrugs off the cold, but it carries little (30) and has the dirtiest exhaust. Diesel (36) is the dependable one. Biodiesel (33) gives up a little energy for half the soot and no sulfur.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("Gasoline (40) and kerosene (38) need a spark plug or a pilot shot. Kerosene holds out against boost better than gasoline. Ethanol (27) and methanol (22) carry least but cool the charge and burn clean, which is what a boosted engine wants.")
			.pointAt(util.vector()
				.topOf(2, 3, 5))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Spilled fuel is not safe the same way. Gasoline and methanol catch at once and burn off in a quick wave. Diesel, biodiesel, kerosene and plant oil are slow to catch and slow to spread. Fuel in an engine's tank never burns this way.")
			.pointAt(util.vector()
				.topOf(2, 3, 5))
			.placeNearTarget();
		scene.idle(150);
		scene.markAsFinished();
	}
}
