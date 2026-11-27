package com.createengines.ponder;

import com.createengines.registry.CEItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

/** Choosing a piston or a rotor by its compression, and the rings that go on a piston. */
public final class PistonScenes {

	private PistonScenes() {
	}

	/** Shot on the precision engine's schematic: one cylinder, open enough to see into. */
	public static void compressionAndRings(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("compression", "Choosing a Piston");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);
		scene.idle(5);
		scene.world()
			.showSection(util.select()
				.fromTo(1, 1, 2, 3, 1, 2)
				.add(util.select()
					.position(2, 2, 2)), Direction.DOWN);
		scene.idle(15);

		scene.overlay()
			.showControls(util.vector()
				.topOf(2, 2, 2), Pointing.DOWN, 90)
			.rightClick()
			.withItem(CEItems.HIGH_COMPRESSION_PISTON.asStack());
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A High-Compression Piston squeezes the charge harder: more power from the same fuel with no boost. Under heavy boost a spark engine lights early sooner, so give it ethanol or methanol.")
			.pointAt(util.vector()
				.centerOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(135);

		scene.overlay()
			.showControls(util.vector()
				.topOf(2, 2, 2), Pointing.DOWN, 90)
			.rightClick()
			.withItem(CEItems.LOW_COMPRESSION_PISTON.asStack());
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A Low-Compression Piston does the opposite: less power with no boost, and room for far more boost on gasoline. A diesel with no plug lights as easily as it did on a Forged Piston.")
			.pointAt(util.vector()
				.centerOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(135);

		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Both are made from a Forged Piston and keep its light weight and low drag. Rotor Housings take High-Compression and Low-Compression Rotors the same way.")
			.pointAt(util.vector()
				.centerOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(115);

		scene.overlay()
			.showControls(util.vector()
				.topOf(2, 2, 2), Pointing.DOWN, 90)
			.rightClick()
			.withItem(CEItems.PISTON_RING.asStack());
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Right-click a fitted piston with up to three Piston Rings. Each seals the charge a little better for a little more power. They never wear. With the rod out, sneak with an empty hand to take them off.")
			.pointAt(util.vector()
				.centerOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(135);
		scene.markAsFinished();
	}
}
