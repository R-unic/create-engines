package com.createengines.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;

/** The opt-in wear mode: what wears, what a gasket and a Service Kit do, and what a failure leaves. Text over a running engine. */
public final class WearScenes {

	private WearScenes() {
	}

	public static void wearAndTear(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("wear_and_tear", "Wear and Tear");
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
			.showText(150)
			.attachKeyFrame()
			.text("Wear and tear is a server option, off by default (wearMode in the config). Switched on, pistons, connecting rods and head gaskets wear as an engine runs: faster the faster it turns, the hotter it runs and the less oil it has, and a gasket faster under boost. A spark engine whose boost lights the charge early knocks, which wears its pistons faster still. A rotor housing's apex seals, a set fitted by right-clicking it, and batteries wear too.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Right-click a head with a gasket to fit it. A fitted gasket seals the joint and is worth a little power even with wear switched off: stock 1%, cardboard 2.5%, rubber 4%, copper 6%. A head with none fitted runs as it always did, so no gasket is ever required.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A worn part costs power and drags harder, and goggles show each part's wear. A Black Box or a Display Link on the engine gives one figure for the whole build, the plain mean of everything that wears. Parts keep their wear when taken out, so a worn piston goes back in as worn as it was.")
			.pointAt(util.vector()
				.centerOf(2, 2, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Service Kit sets light wear right, to a tenth, on a cylinder or head, or on a part held in the other hand. A Deployer holding one does the same to parts on a belt, in a basin or on a depot. A kit has 24 uses. Heavy wear needs a new part, and sneaking with a kit on a head takes its gasket out.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("A badly worn gasket can blow: the cylinder under it stops, smoking, until a gasket is fitted. A badly worn rod can be thrown. The piston and rod are lost, the head breaks, and a wreck stays in the cylinder, so the head is replaced and a Service Kit clears the wreck before new parts go in.")
			.pointAt(util.vector()
				.centerOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.RED)
			.text("Run a piston without oil for about twenty seconds and it seizes: the cylinder locks and drags on the crank, and the piston is left badly worn. A Service Kit frees it, or take the piston out. Keep the oil in, and the goggles will warn you before it comes to that.")
			.pointAt(util.vector()
				.centerOf(2, 2, 3))
			.placeNearTarget();
		scene.idle(150);
	}
}
