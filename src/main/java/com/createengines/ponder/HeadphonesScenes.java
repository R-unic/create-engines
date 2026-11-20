package com.createengines.ponder;

import com.createengines.registry.CEItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

/** The noise-cancelling headphones: what they muffle, what they leave alone, and where they are worn. */
public final class HeadphonesScenes {

	private HeadphonesScenes() {
	}

	public static void headphones(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("headphones", "Muffling the World");
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

		ItemStack headphones = new ItemStack(CEItems.HEADPHONES.get());
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A muffler quiets one engine's exhaust. Noise-Cancelling Headphones quiet everything you hear from the world: engines, machines, mobs, weather and the rest.")
			.pointAt(util.vector()
				.topOf(2, 3, 5))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showControls(util.vector()
				.centerOf(4, 2, 1), Pointing.DOWN, 60)
			.rightClick()
			.withItem(headphones);
		scene.idle(20);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Wear them on your head, like Create's goggles, or in a Curios head slot if you have Curios. Use them in your hand to put them on.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("They muffle, they do not mute: sounds come through quieter and duller, a warning or a sting is still heard. Only you hear the change.")
			.pointAt(util.vector()
				.topOf(2, 3, 5))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(120)
			.text("Music, menu sounds and voice chat are left as they are, since they do not come from the world.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(130);
		scene.markAsFinished();
	}
}
