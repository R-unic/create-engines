package com.createengines.ponder;

import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

/** The three sizes of battery: what each holds, how the cold treats it, and that they join into one bank. Text over a spark engine. */
public final class BatteryScenes {

	private BatteryScenes() {
	}

	public static void batterySizes(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("battery_sizes", "Batteries: Three Sizes");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		Selection battery = util.select()
			.position(1, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 2, 3, 2, 3, 3)
			.add(util.select()
				.fromTo(2, 3, 2, 2, 3, 4))
			.add(util.select()
				.fromTo(1, 4, 3, 3, 4, 3));

		EngineScenes.setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		EngineScenes.setLobe(scene, util.select()
			.position(2, 4, 3), EngineScenes.LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.showSection(battery, Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("The Battery holds 200 sparks. Two other sizes sit beside it: the small Dry Cell and the big Large Battery. All three work the same way and look after the same plugs and starters.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.setBlock(util.grid()
				.at(0, 2, 3), CEBlocks.DRY_CELL.getDefaultState(), false);
		scene.world()
			.showSection(util.select()
				.position(0, 2, 3), Direction.EAST);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("A Dry Cell holds 100 sparks, half a Battery, but the cold does not touch it. Put it outdoors in a snowy biome, or away from any heat, and it still gives everything it holds.")
			.pointAt(util.vector()
				.topOf(0, 2, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.setBlock(util.grid()
				.at(1, 2, 4), CEBlocks.LARGE_BATTERY.getDefaultState(), false);
		scene.world()
			.showSection(util.select()
				.position(1, 2, 4), Direction.NORTH);
		scene.idle(15);
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.text("A Large Battery holds 800 sparks, four Batteries in one block, the bank for a big engine with several starters and plugs. In the deepest cold it can give up only a quarter of it, so keep it somewhere warm.")
			.pointAt(util.vector()
				.topOf(1, 2, 4))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Batteries of any size that touch are one bank: the capacity adds up, and whatever touches one reaches all of them. The lamps on each lid show its own charge, and a Comparator reads the whole bank.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(130)
			.colored(PonderPalette.BLUE)
			.text("A Heat source, such as a Blaze Burner, beside any of them warms it. Goggles on a battery say its size, how much it holds and how much of that the cold lets it give right now.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

}
