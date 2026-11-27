package com.createengines.ponder;

import com.createengines.content.airfilter.AirFilterBlock;
import com.createengines.content.airflow.PortOpeningBlock;
import com.createengines.registry.CEBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** The air filter, for packs where the air can be foul. */
public final class AirFilterScenes {

	private AirFilterScenes() {
	}

	/** Shot on the intake and exhaust schematic, with the air intake moved out a block and a filter put where it was. */
	public static void airFilter(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("air_filter", "Cleaning the Intake Air");
		scene.configureBasePlate(0, 0, 6);
		BlockPos filter = util.grid()
			.at(1, 3, 2);
		BlockPos mouth = filter.west();
		scene.world()
			.setBlock(filter, CEBlocks.AIR_FILTER.getDefaultState()
				.setValue(AirFilterBlock.AXIS, Direction.Axis.X), false);
		scene.world()
			.setBlock(mouth, CEBlocks.AIR_INTAKE.getDefaultState()
				.setValue(PortOpeningBlock.FACING, Direction.WEST), false);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);
		scene.idle(5);
		scene.world()
			.showSection(util.select()
				.fromTo(1, 1, 3, 4, 1, 3)
				.add(util.select()
					.fromTo(2, 2, 3, 3, 3, 3))
				.add(util.select()
					.fromTo(0, 3, 2, 3, 3, 2)), Direction.DOWN);
		scene.idle(20);

		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("With Pollution of the Realms, a server can make engines breathe the air at their intakes. Carbon and sulfur there cost an engine some of its air, and so some power.")
			.pointAt(util.vector()
				.blockSurface(mouth, Direction.WEST))
			.placeNearTarget();
		scene.idle(135);
		scene.overlay()
			.showOutline(PonderPalette.GREEN, filter, util.select()
				.position(filter), 120);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("An Air Filter in the intake run takes most of that back. If the air can reach the engine by a branch that goes round the filter, it helps much less.")
			.pointAt(util.vector()
				.topOf(filter))
			.placeNearTarget();
		scene.idle(135);
		scene.overlay()
			.showText(100)
			.text("The crankshaft's goggles show what dirty air is costing. A filter never wears out, and with polluted intake air off it is just a length of pipe.")
			.pointAt(util.vector()
				.topOf(filter))
			.placeNearTarget();
		scene.idle(110);
		scene.markAsFinished();
	}
}
