package com.createengines.ponder;

import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
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
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Use a Wrench on a battery to make it a reserve (sneak with it still picks the battery up). A reserve is kept for the starter and the spark plugs: injector assists, ignition coils and other mods' wires cannot draw on it, and an Alternator tops it up first.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(150);

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

	/** The two parts that spend charge for a better shot and a better spark: the Injector Assist and the Ignition Coil. */
	public static void electricParts(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("electric_parts", "Spending Charge: Injector Assist and Ignition Coil");
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
		BlockPos head = util.grid()
			.at(2, 3, 3);

		EngineScenes.setThrow(scene, util.select()
			.position(2, 1, 3), 0);
		EngineScenes.setLobe(scene, util.select()
			.position(2, 4, 3), EngineScenes.LOBE_FOR_UPRIGHT_CYLINDER);
		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.modifyBlock(head, s -> s.setValue(CylinderHeadBlock.HAS_INJECTOR, true)
				.setValue(CylinderHeadBlock.HAS_SPARK_PLUG, true), false);
		scene.world()
			.showSection(battery, Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("Two extras for a Cylinder Head spend Battery charge. Both are only a bonus: with no charge to spare the head works exactly as it would without them.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.modifyBlock(head, s -> s.setValue(CylinderHeadBlock.HAS_ASSIST, true), false);
		scene.idle(10);
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("An Injector Assist, right-clicked onto a head that has an injector, spends half a spark on every shot to atomise the fuel finer: it lights sooner, burns faster and the injector can pass half again as much at its limit. Take it off before the injector.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(160);

		scene.world()
			.modifyBlock(head, s -> s.setValue(CylinderHeadBlock.HAS_COIL, true), false);
		scene.idle(10);
		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("An Ignition Coil sits on a head's Spark Plug and spends half a spark more on each spark that has fuel to light. The hotter spark lights a leaner mixture, burns the charge a little faster and burns through soot that would foul a plain plug.")
			.pointAt(util.vector()
				.topOf(head))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(130)
			.text("Both draw only from batteries that are not a reserve, so a Wrench on a battery keeps its charge for the starter and the plugs. Goggles on the head say what is fitted; the Dyno rates them only while the bank can spare the charge.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

}
