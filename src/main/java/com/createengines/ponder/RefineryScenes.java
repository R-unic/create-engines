package com.createengines.ponder;

import com.createengines.content.refinery.ColumnSectionBlock;
import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEComponents;
import com.createengines.registry.CEFluids;
import com.createengines.registry.CEItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Ponder scenes for the refinery parts: the column, a leaning column, the soot trap and the jerry can. */
public final class RefineryScenes {

	private RefineryScenes() {
	}

	private static void setActive(CreateSceneBuilder scene, Selection sections, boolean active) {
		scene.world()
			.modifyBlocks(sections, s -> s.setValue(ColumnSectionBlock.ACTIVE, active), false);
	}

	public static void refinery(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("refinery", "Refining in a Column");
		scene.configureBasePlate(0, 0, 7);
		scene.scaleSceneView(0.8f);
		scene.setSceneOffsetY(-1f);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection heat = util.select()
			.fromTo(2, 1, 2, 3, 1, 3);
		Selection reboilers = util.select()
			.fromTo(2, 2, 2, 3, 2, 3);
		Selection sections = util.select()
			.fromTo(2, 3, 2, 3, 4, 3);
		Selection condensers = util.select()
			.fromTo(2, 5, 2, 3, 5, 3);
		Selection flare = util.select()
			.position(2, 6, 2);
		Selection feedPlumbing = util.select()
			.fromTo(0, 1, 3, 1, 2, 3);
		Selection feedPump = util.select()
			.position(1, 2, 3);
		Selection productPlumbing = util.select()
			.fromTo(0, 5, 2, 1, 5, 2);
		Selection productPump = util.select()
			.position(1, 5, 2);
		BlockPos feedTank = util.grid()
			.at(0, 2, 3);
		BlockPos productTank = util.grid()
			.at(0, 5, 2);
		scene.world()
			.modifyBlockEntity(feedTank, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.fill(new FluidStack(CEFluids.PLANT_OIL.get()
					.getSource(), 8000), FluidAction.EXECUTE));

		scene.world()
			.showSection(heat, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(reboilers, Direction.DOWN);
		scene.idle(10);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("A refining column makes fuel from feed in one pass. The base is a square of Reboilers, one to three blocks wide, set on a Blaze Burner or other heat.")
			.pointAt(util.vector()
				.topOf(2, 2, 2))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.showSection(sections, Direction.DOWN);
		scene.idle(15);
		scene.world()
			.showSection(condensers, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Column Sections go on the Reboilers, as many layers as you like up to thirty-two, and a layer of Condensers closes the top. Every layer is the same square.")
			.pointAt(util.vector()
				.topOf(3, 4, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.world()
			.showSection(feedPlumbing, Direction.EAST);
		scene.world()
			.showSection(productPlumbing, Direction.EAST);
		scene.idle(15);
		scene.world()
			.setKineticSpeed(feedPump, 32);
		scene.world()
			.setKineticSpeed(productPump, 32);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Pump feed into any Reboiler and take the product from any Condenser. The Reboilers only fill and the Condensers only drain, so a pipe cannot run it backwards.")
			.pointAt(util.vector()
				.blockSurface(new BlockPos(1, 2, 3), Direction.UP))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showControls(util.vector()
				.topOf(3, 2, 3), Pointing.DOWN, 40)
			.rightClick()
			.withItem(new ItemStack(Items.COAL));
		scene.idle(50);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("Some routes use up a catalyst: diesel and gasoline burn coal and blaze powder, ethanol takes Nether Wart. Hand it to a Reboiler, or let a funnel or hopper feed it.")
			.pointAt(util.vector()
				.topOf(3, 2, 3))
			.placeNearTarget();
		scene.idle(140);

		setActive(scene, sections, true);
		// The scene has no column controller, so the tanks are moved by hand: feed drains, product collects
		for (int i = 0; i < 4; i++) {
			scene.world()
				.modifyBlockEntity(feedTank, FluidTankBlockEntity.class, be -> be.getTankInventory()
					.drain(1000, FluidAction.EXECUTE));
			scene.world()
				.modifyBlockEntity(productTank, FluidTankBlockEntity.class, be -> be.getTankInventory()
					.fill(new FluidStack(CEFluids.DIESEL.get()
						.getSource(), 1100), FluidAction.EXECUTE));
			scene.idle(8);
		}
		scene.world()
			.modifyBlock(util.grid()
				.at(2, 6, 2), s -> s.setValue(com.createengines.content.refinery.FlareStackBlock.LIT, true), false);
		scene.world()
			.showSection(flare, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("While it works the sight windows glow. The heat under the base picks the route, a wider and taller column runs faster, and the product is worth more than the same feed in a basin. A Flare Stack on a Condenser burns while it runs.")
			.pointAt(util.vector()
				.topOf(2, 6, 2))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(110)
			.text("Goggles on any part say what is stopping it: no feed, too cold, no catalyst, or product full.")
			.pointAt(util.vector()
				.blockSurface(new BlockPos(2, 3, 2), Direction.WEST))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void refineryLean(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("refinery_lean", "Leaning Columns");
		scene.configureBasePlate(0, 0, 7);
		scene.scaleSceneView(0.7f);
		scene.setSceneOffsetY(-1.5f);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection lower = util.select()
			.fromTo(2, 1, 3, 2, 3, 3);
		Selection middle = util.select()
			.position(3, 4, 3);
		Selection upper = util.select()
			.position(4, 5, 3);
		Selection top = util.select()
			.position(4, 6, 3);

		scene.world()
			.showSection(lower, Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A column does not have to stand straight. After the first layer, each layer may sit one block to the side of the one below.")
			.pointAt(util.vector()
				.topOf(2, 3, 3))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(middle, Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Sections that meet corner to corner join by themselves, the way Create chutes do. There is no joint block to place.")
			.pointAt(util.vector()
				.blockSurface(new BlockPos(3, 4, 3), Direction.WEST))
			.placeNearTarget();
		scene.idle(130);

		scene.world()
			.showSection(upper, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(top, Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.text("It can go on leaning the same way, or step off the other way after a straight layer. Straight back at once is refused.")
			.pointAt(util.vector()
				.topOf(4, 6, 3))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showText(120)
			.colored(PonderPalette.RED)
			.text("The braces are drawn by the sections and take no block, but the space beside a bend must be empty. If something is in the way, goggles name the block.")
			.pointAt(util.vector()
				.blockSurface(new BlockPos(3, 4, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(130);
		scene.markAsFinished();
	}

	public static void sootTrap(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("soot_trap", "Catching Soot");
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
		Selection trap = util.select()
			.position(2, 3, 5);
		Selection hopper = util.select()
			.position(2, 2, 5);
		BlockPos trapPos = util.grid()
			.at(2, 3, 5);

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
			.showText(110)
			.attachKeyFrame()
			.text("A rich-running engine sends unburned fuel out of the pipe as soot.")
			.pointAt(util.vector()
				.topOf(2, 3, 6))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(trap, Direction.DOWN);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A Soot Trap set in the exhaust run catches it: every 50 mB of unburned fuel makes one Soot, up to 64. It quiets the exhaust like a Muffler too.")
			.pointAt(util.vector()
				.topOf(trapPos))
			.placeNearTarget();
		scene.idle(140);

		scene.overlay()
			.showControls(util.vector()
				.topOf(trapPos), Pointing.DOWN, 40)
			.rightClick();
		scene.idle(50);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Use it with an empty hand to take the soot out. Goggles say how much it holds.")
			.pointAt(util.vector()
				.topOf(trapPos))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.showSection(hopper, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(130)
			.text("A hopper or funnel under it empties it for you. Soot grinds to black dye. A full trap lets the soot through to the pipe again.")
			.pointAt(util.vector()
				.blockSurface(new BlockPos(2, 2, 5), Direction.EAST))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void jerryCan(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("jerry_can", "Carrying Fuel");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		BlockPos source = util.grid()
			.at(1, 1, 2);
		BlockPos target = util.grid()
			.at(4, 1, 2);
		ItemStack can = new ItemStack(CEItems.JERRY_CAN.get());
		ItemStack full = can.copy();
		full.set(CEComponents.CAN_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(CEFluids.DIESEL.get()
			.getSource(), 4000)));

		scene.world()
			.modifyBlockEntity(source, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.fill(new FluidStack(CEFluids.DIESEL.get()
					.getSource(), 8000), FluidAction.EXECUTE));
		scene.world()
			.showSection(util.select()
				.position(source), Direction.DOWN);
		scene.world()
			.showSection(util.select()
				.position(target), Direction.DOWN);
		scene.idle(20);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("A Jerry Can carries four buckets of one fluid, for fuel that has to go where there is no pipe.")
			.pointAt(util.vector()
				.topOf(source))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.topOf(source), Pointing.DOWN, 40)
			.rightClick()
			.withItem(can);
		scene.idle(50);
		scene.overlay()
			.showControls(util.vector()
				.topOf(target), Pointing.DOWN, 40)
			.rightClick()
			.withItem(full);
		scene.world()
			.modifyBlockEntity(source, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.drain(4000, FluidAction.EXECUTE));
		scene.world()
			.modifyBlockEntity(target, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.fill(new FluidStack(CEFluids.DIESEL.get()
					.getSource(), 4000), FluidAction.EXECUTE));
		scene.idle(50);
		scene.overlay()
			.showText(130)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("It fills and empties the way a bucket does, in any tank, and Spouts and Item Drains work it too. It holds one fluid at a time and does not stack.")
			.pointAt(util.vector()
				.topOf(target))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}
}
