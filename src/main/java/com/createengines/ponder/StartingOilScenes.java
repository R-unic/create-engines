package com.createengines.ponder;

import static com.createengines.ponder.SceneHelpers.*;

import com.createengines.registry.CEBlocks;
import com.createengines.registry.CEFluids;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Ponder scenes for starting and oil. Text here is the English source; datagen writes it to the lang file. */
public final class StartingOilScenes {

	private StartingOilScenes() {
	}

	public static void lubrication(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("lubrication", "Keeping an Engine Oiled");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 1, 3, 3, 1, 3);
		BlockPos head = util.grid()
			.at(2, 3, 3);
		BlockPos tank = util.grid()
			.at(4, 3, 3);
		scene.world()
			.modifyBlockEntity(util.grid()
				.at(4, 3, 3), FluidTankBlockEntity.class, be -> be.getTankInventory()
					.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
						.getSource(), 4000), FluidAction.EXECUTE));
		scene.idle(10);

		scene.world()
			.showSection(util.select()
				.fromTo(1, 1, 3, 2, 3, 3), Direction.DOWN);
		scene.idle(10);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("Crankshaft bearings, pistons and connecting rods all rub against something. They need a film of oil to slide.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 2, 3), Direction.NORTH))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(100)
			.colored(PonderPalette.RED)
			.text("A new engine is dry. Dry parts drag: an engine with no oil still runs, but tops out at about half its speed and has less torque to spare.")
			.pointAt(util.vector()
				.topOf(2, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(util.select()
				.fromTo(3, 3, 3, 4, 3, 3), Direction.WEST);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Pipe Engine Oil into any face of a Cylinder Head. It has a second tank for oil, apart from the fuel. Every head on the crankshaft shares it.")
			.pointAt(util.vector()
				.blockSurface(head, Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.setKineticSpeed(crank, 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Once oil reaches the engine, the film builds within seconds and friction drops. The engine can turn faster and has more torque to spare.")
			.pointAt(util.vector()
				.topOf(2, 1, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(100)
			.text("Turning wears the oil off a little at a time. If the supply runs dry the film thins and the engine slows, so keep the tank topped up.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(90)
			.text("Goggles on any crankshaft block show how well the engine is lubricated.")
			.pointAt(util.vector()
				.topOf(1, 1, 3))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(90)
			.text("Engine Oil is made by heating Plant Oil with a Slime Ball in a Mechanical Mixer over a Blaze Burner.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.EAST))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}

	public static void sump(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("sump", "An Oil Sump");
		scene.configureBasePlate(0, 0, 6);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection crank = util.select()
			.fromTo(1, 2, 3, 3, 2, 3);
		Selection engine = util.select()
			.fromTo(2, 3, 3, 2, 4, 3);
		Selection sump = util.select()
			.position(3, 1, 3);
		Selection plumbing = util.select()
			.fromTo(4, 1, 3, 5, 1, 3);
		BlockPos sumpPos = util.grid()
			.at(3, 1, 3);
		BlockPos tank = util.grid()
			.at(5, 1, 3);
		scene.world()
			.modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> be.getTankInventory()
				.fill(new FluidStack(CEFluids.ENGINE_OIL.get()
					.getSource(), 4000), FluidAction.EXECUTE));
		scene.idle(10);

		scene.world()
			.showSection(crank, Direction.DOWN);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.world()
			.setKineticSpeed(crank, 32);
		scene.idle(15);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("Engine oil lubricates the whole engine, not one cylinder, so it needs only one place to come in. A head's faces are better spent on intake, exhaust and fuel.")
			.pointAt(util.vector()
				.blockSurface(util.grid()
					.at(2, 4, 3), Direction.EAST))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(sump, Direction.UP);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("An Oil Sump set against any crankshaft block or cylinder head is the engine's oil pan. It holds a litre of oil, and the engine draws on it as it turns.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showControls(util.vector()
				.blockSurface(sumpPos, Direction.UP), Pointing.DOWN, 50)
			.rightClick()
			.withItem(new ItemStack(CEFluids.ENGINE_OIL.getBucket()
				.orElseThrow()));
		scene.idle(15);
		scene.world()
			.setKineticSpeed(crank, 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Fill it by hand with a bucket of Engine Oil. The film builds within seconds and friction drops.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.UP))
			.placeNearTarget();
		scene.idle(110);

		scene.world()
			.showSection(plumbing, Direction.WEST);
		scene.idle(15);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Or pipe oil into any of its faces from a tank. Pipes carry one fluid at a time, so a sump takes the oil line and the head keeps its fuel line.")
			.pointAt(util.vector()
				.blockSurface(tank, Direction.UP))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(90)
			.text("Cylinder heads still take oil too, and the sump is drawn on first. Goggles on the sump show what is left.")
			.pointAt(util.vector()
				.topOf(sumpPos))
			.placeNearTarget();
		scene.idle(100);

		scene.overlay()
			.showText(140)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Oil goes off with use, and a spent charge drags a little more. Scroll the dial on the sump to set an Oil Exchange: spent oil is pushed out as Used Oil while fresh oil piped in takes its place.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(140)
			.colored(PonderPalette.RED)
			.text("Used Oil has to be piped away or the exchange stops. Burn it as a poor fuel, refine it back into oil, coke it into charcoal, or let it go. An Oil Filter, fitted by right-click, slows the ageing and cleans fuel out of the oil.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(150);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("The same sump takes coolant, from any face. Water works; Engine Coolant, made from water and glycerin, works better. It is spent carrying heat away; a Radiator is a loop that keeps its coolant instead. A cool engine breathes better. A cold one drags until it warms up. One with too little cooling overheats and loses power from 140 degrees until it cools. With wear off, nothing breaks.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.UP))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.colored(PonderPalette.WHITE)
			.text("It also takes a pilot fuel: pipe diesel into it and a compression engine with no spark plug injects a small pilot shot that lights gasoline or ethanol, a dual-fuel engine.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.WEST))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(150)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("A comparator against the sump reads one tank: 0 when it is empty, 15 when it is full. Scroll the upper dial to pick Engine Oil, Used Oil, Coolant or Pilot Fuel. Use it to switch a pump or sound an alarm when the oil runs low.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.NORTH))
			.placeNearTarget();
		scene.idle(160);

		scene.overlay()
			.showText(110)
			.colored(PonderPalette.BLUE)
			.text("A Display Link set against the sump lists the levels of all four tanks on a display board.")
			.pointAt(util.vector()
				.blockSurface(sumpPos, Direction.EAST))
			.placeNearTarget();
		scene.idle(120);
		scene.markAsFinished();
	}

	public static void starter(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("starter", "Starting an Engine");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		Selection engine = util.select()
			.fromTo(1, 2, 3, 3, 4, 3);
		BlockPos starter = util.grid()
			.at(1, 2, 2);
		Selection input = util.select()
			.fromTo(1, 2, 0, 1, 2, 1);
		Selection power = util.select()
			.position(0, 2, 2);

		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(util.select()
				.position(starter), Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(80)
			.attachKeyFrame()
			.text("A Starter cranks the crankshaft block it faces. It is not connected to the engine's own kinetic network.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.UP))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(input, Direction.SOUTH);
		scene.world()
			.setKineticSpeed(input, 64);
		scene.world()
			.setKineticSpeed(util.select()
				.position(starter), 64);
		scene.idle(10);
		scene.overlay()
			.showText(80)
			.text("Feed it rotation through its back from any source. It cranks the engine at the speed it is turned. A compression engine, like a diesel, can only be started this way.")
			.pointAt(util.vector()
				.topOf(1, 2, 1))
			.placeNearTarget();
		scene.idle(90);

		scene.world()
			.showSection(power, Direction.DOWN);
		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 64);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(0, 2, 2));
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.text("It engages while powered by redstone. Turning a dead engine over takes effort, which appears as stress on the starter's input.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.WEST))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(170)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.text("A spark engine with a charged Battery touching it needs no input rotation at all: with the starter's shaft still, a redstone signal makes it spend charge to turn the engine over, slowly. A flat bank stops that. A compression engine has no plugs and takes no charge, so it always needs rotation.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.WEST))
			.placeNearTarget();
		scene.idle(180);

		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 96);
		scene.overlay()
			.showText(100)
			.attachKeyFrame()
			.colored(PonderPalette.GREEN)
			.text("Once the engine fires and runs faster than the starter, a one-way clutch lets go. Remove the signal and it carries on by itself.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(110);

		scene.overlay()
			.showText(130)
			.text("A Hand Crank on the crankshaft also works for small engines. So does one turning the Starter's own shaft, which engages it with no redstone signal at all, so a flat battery is never a dead end. Bigger engines need stronger starters.")
			.pointAt(util.vector()
				.topOf(1, 2, 3))
			.placeNearTarget();
		scene.idle(140);
		scene.markAsFinished();
	}

	public static void heavyStarter(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("heavy_starter", "The Heavy Starter");
		scene.configureBasePlate(0, 0, 5);
		scene.world()
			.showSection(util.select()
				.layer(0), Direction.UP);

		BlockPos starter = util.grid()
			.at(1, 2, 2);
		Selection starterSelection = util.select()
			.position(starter);
		Selection engine = util.select()
			.fromTo(1, 2, 3, 3, 4, 3);
		Selection input = util.select()
			.fromTo(1, 2, 0, 1, 2, 1);
		Selection power = util.select()
			.position(0, 2, 2);

		// The page reuses the Starter's setup, with the Starter swapped for the heavy one before anything is shown
		scene.world()
			.replaceBlocks(starterSelection, CEBlocks.HEAVY_STARTER.getDefaultState()
				.setValue(BlockStateProperties.FACING, Direction.SOUTH), false);
		scene.world()
			.showSection(engine, Direction.DOWN);
		scene.idle(10);
		scene.world()
			.showSection(starterSelection, Direction.SOUTH);
		scene.idle(15);
		scene.overlay()
			.showText(90)
			.attachKeyFrame()
			.text("The Heavy Starter is a Starter with reduction gearing. It is placed, fed and switched exactly like one, and cranks the crankshaft block it faces.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.UP))
			.placeNearTarget();
		scene.idle(100);

		scene.world()
			.showSection(input, Direction.SOUTH);
		scene.world()
			.showSection(power, Direction.DOWN);
		scene.world()
			.setKineticSpeed(input, 64);
		scene.world()
			.setKineticSpeed(starterSelection, 64);
		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 64);
		scene.effects()
			.indicateRedstone(util.grid()
				.at(0, 2, 2));
		scene.idle(10);
		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("It takes on two and a half times the effort of a plain Starter, so it puts that much less stress on its own input. A big engine that would stall a weak source can be turned over by one.")
			.pointAt(util.vector()
				.topOf(1, 2, 1))
			.placeNearTarget();
		scene.idle(120);

		scene.overlay()
			.showText(110)
			.attachKeyFrame()
			.text("Starters working together share the crank by strength. Two Heavy Starters on one engine feel a fifth of a plain Starter's effort each.")
			.pointAt(util.vector()
				.blockSurface(starter, Direction.WEST))
			.placeNearTarget();
		scene.idle(120);

		scene.world()
			.setKineticSpeed(util.select()
				.fromTo(1, 2, 3, 3, 2, 3), 96);
		scene.overlay()
			.showText(120)
			.attachKeyFrame()
			.text("It also chokes a cold engine while it cranks, so the cold does not hold the engine back from catching. It keeps choking for a few seconds after it lets go, so the engine can settle.")
			.pointAt(util.vector()
				.topOf(2, 4, 3))
			.placeNearTarget();
		scene.idle(130);

		scene.overlay()
			.showText(90)
			.text("Like a plain Starter, a one-way clutch lets go once the engine outruns it, and a Hand Crank on its shaft engages it with no signal. It too can turn a spark engine from its Battery with nothing on its input, but a compression engine always needs rotation.")
			.pointAt(util.vector()
				.topOf(1, 2, 1))
			.placeNearTarget();
		scene.idle(100);
		scene.markAsFinished();
	}
}
