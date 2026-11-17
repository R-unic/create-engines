package com.createengines.ponder;

import com.createengines.registry.CEItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** The Blueprint: recording an engine, reading the sheet, and copying a tune onto another build. */
public final class BlueprintScenes {

	private BlueprintScenes() {
	}

	public static void blueprint(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("blueprint", "Recording an Engine");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection engine = util.select()
			.fromTo(1, 2, 3, 3, 4, 3);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(20);

		ItemStack blueprint = new ItemStack(CEItems.BLUEPRINT.get());
		BlockPos cylinder = util.grid()
			.at(2, 3, 3);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("A blank Blueprint records an engine. Use it on any block of a built engine and it remembers every block, how each is fitted, and every dial: throw angles, cam timing and fuel metering.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(20);
		scene.overlay()
			.showControls(util.vector()
				.topOf(cylinder), Pointing.DOWN, 50)
			.rightClick()
			.withItem(blueprint);
		scene.idle(130);

		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Use it in the air to read it as a drawing: the engine seen from above, its parts, and the figures from a Maker's Plate if the engine wears one.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.text("Use a recorded Blueprint on an engine of the same build and every dial is set to match. If anything is built differently, nothing changes and the first differences are named.")
			.pointAt(util.vector()
				.topOf(new BlockPos(2, 4, 3)))
			.placeNearTarget();
		scene.idle(20);
		scene.overlay()
			.showControls(util.vector()
				.topOf(new BlockPos(2, 4, 3)), Pointing.DOWN, 50)
			.rightClick()
			.withItem(blueprint);
		scene.idle(130);

		scene.overlay()
			.showText(120)
			.text("Only this mod's blocks are recorded, never the shafts and cogwheels of Create. Sneak and use a recorded Blueprint on an engine to record over it.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.text("Hold a Bank Builder with a recorded Blueprint in the other hand and it lays the whole engine out from the parts you carry, crankshaft first, if the site is clear and you may build there. Nothing is placed unless every part is there.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.text("Sneak and use a recorded Blueprint in the air to copy it as text. A blank one used in the air asks you to paste a blueprint someone shared; the server checks every block and dial before it keeps it. An Empty Schematic in the other hand turns it into a Create schematic.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(120)
			.text("A Framed Blueprint hangs it on a wall as its drawing. Use a recorded Blueprint on the frame, and sneak with an empty hand to take it back.")
			.pointAt(util.vector()
				.topOf(cylinder))
			.placeNearTarget();
		scene.idle(130);
		scene.markAsFinished();
	}
}
