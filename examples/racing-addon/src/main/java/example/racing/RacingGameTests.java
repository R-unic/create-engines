package example.racing;

import java.util.Optional;

import com.createengines.api.EngineApi;
import com.createengines.api.EngineStatus;
import com.createengines.api.EngineView;
import com.createengines.api.EngineBuilding;
import com.createengines.api.PartBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The addon's own GameTests, in a game that has Create: Engine Engineer loaded as an ordinary jar. They look at engines only
 * through the mod's public API ({@link EngineApi}) and through the registry, the way any addon would.
 */
@GameTestHolder(RacingAddon.ID)
@PrefixGameTestTemplate(false)
public class RacingGameTests {

	private static final BlockPos PLAIN = BlockPos.ZERO;
	private static final BlockPos RACING = new BlockPos(14, 0, 14);
	private static final BlockPos CRANK = new BlockPos(1, 2, 2);

	private static Block engines(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create_engines", path));
	}

	/** A block's default state with some of its properties set by name, so this test needs none of the mod's own classes. */
	private static BlockState state(Block block, String... nameAndValue) {
		BlockState state = block.defaultBlockState();
		for (int i = 0; i < nameAndValue.length; i += 2)
			state = with(state, nameAndValue[i], nameAndValue[i + 1]);
		return state;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState with(BlockState state, String name, String value) {
		Property property = state.getBlock()
			.getStateDefinition()
			.getProperty(name);
		if (property == null)
			throw new IllegalStateException(state.getBlock() + " has no property " + name);
		Optional<?> parsed = property.getValue(value);
		return state.setValue(property, (Comparable) parsed.orElseThrow(() -> new IllegalStateException(name + " cannot be " + value)));
	}

	/**
	 * One single-cylinder diesel engine with {@code cylinder} in the bore, its corner at {@code origin}: the mod's own test engine
	 * rebuilt from the registry and Create's blocks, with a creative motor and a starter to run it.
	 */
	private static void build(Level level, BlockPos origin, Block cylinder) {
		build(level, origin, cylinder, null);
	}

	/** As above, with {@code flywheel} (if not null) on the crank's free end, its shaft along the crank's. */
	private static void build(Level level, BlockPos origin, Block cylinder, Block flywheel) {
		if (flywheel != null)
			level.setBlock(origin.offset(new BlockPos(0, 2, 2)), state(flywheel, "axis", "x"), Block.UPDATE_ALL);
		java.util.function.BiConsumer<BlockPos, BlockState> set = (pos, s) -> level.setBlock(origin.offset(pos), s, Block.UPDATE_ALL);
		set.accept(CRANK, state(engines("crankshaft"), "axis", "x"));
		set.accept(new BlockPos(2, 2, 2), state(engines("crank_throw"), "axis", "x"));
		set.accept(new BlockPos(3, 2, 2), state(engines("crankshaft"), "axis", "x"));
		set.accept(new BlockPos(4, 2, 2), AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		set.accept(new BlockPos(5, 2, 2), AllBlocks.COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		set.accept(new BlockPos(2, 3, 2), state(cylinder, "facing", "up", "piston", "true", "connecting_rod", "true"));
		set.accept(new BlockPos(2, 4, 2), state(engines("cylinder_head"), "facing", "up", "injector", "true"));
		set.accept(new BlockPos(2, 4, 1), state(engines("air_intake"), "facing", "north"));
		set.accept(new BlockPos(1, 4, 2), state(engines("exhaust_outlet"), "facing", "west"));
		// The camshaft beside the head, geared to half the crank's speed the Create way
		set.accept(new BlockPos(2, 4, 3), state(engines("cam_lobe"), "axis", "x"));
		for (int x = 3; x <= 5; x++)
			set.accept(new BlockPos(x, 4, 3), state(engines("camshaft"), "axis", "x"));
		set.accept(new BlockPos(5, 3, 3), AllBlocks.LARGE_COGWHEEL.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		set.accept(new BlockPos(6, 3, 3), AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		set.accept(new BlockPos(6, 4, 3), AllBlocks.ENCASED_CHAIN_DRIVE.getDefaultState().setValue(BlockStateProperties.AXIS, Direction.Axis.X));
		// A starter under the first journal, a creative motor under it, and a redstone block to engage it
		set.accept(new BlockPos(1, 1, 2), state(engines("starter"), "facing", "up"));
		set.accept(new BlockPos(1, 0, 2), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.UP));
		set.accept(new BlockPos(0, 1, 2), Blocks.REDSTONE_BLOCK.defaultBlockState());
		((CreativeMotorBlockEntity) level.getBlockEntity(origin.offset(new BlockPos(1, 0, 2)))).generatedSpeed.setValue(80);

		// Time the cam lobe so it fires the cylinder with its piston at top dead centre
		EngineBuilding.timeCamLobe(level, origin.offset(new BlockPos(2, 4, 3)), Direction.UP, Direction.Axis.X);

		// Oil and diesel go in through the head's fluid capability, the way a pipe or a bucket would
		IFluidHandler head = level.getCapability(Capabilities.FluidHandler.BLOCK, origin.offset(new BlockPos(2, 4, 2)), null);
		head.fill(new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("create_engines", "engine_oil")), 250), IFluidHandler.FluidAction.EXECUTE);
		head.fill(new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("create_engines", "diesel")), 250), IFluidHandler.FluidAction.EXECUTE);
	}

	private static Optional<EngineView> view(GameTestHelper helper, BlockPos origin) {
		return EngineApi.viewAt(helper.getLevel(), helper.absolutePos(origin.offset(CRANK)));
	}

	@GameTest(template = "empty_large", timeoutTicks = 200)
	public static void theRacingCylinderIsAnEngineBlockAndNamesItsPartThroughTheDataMap(GameTestHelper helper) {
		// The data map entry shipped in this addon's jar, read by the mod: no override, no test hook
		var effect = PartBlocks.partEffect(RacingAddon.RACING_CYLINDER.get());
		helper.assertTrue(effect.isPresent() && effect.get()
			.toString()
			.equals("racing_addon:racing_bore"), "the data map names the part effect, but said: " + effect);
		helper.assertTrue(PartBlocks.partEffect(engines("cylinder"))
			.isEmpty(), "the mod's own cylinder names no part effect");
		helper.succeed();
	}

	/**
	 * The Racing Flywheel is a flywheel of this addon's own, made with {@link PartBlocks#flywheel} and given its masses by the
	 * {@code flywheel} data map. Its mass is the engine's to carry: beside a plain engine it revs up more slowly, and it runs.
	 */
	@GameTest(template = "empty_large", timeoutTicks = 700)
	public static void anEngineWithTheRacingFlywheelCarriesItsMassAndRuns(GameTestHelper helper) {
		build(helper.getLevel(), helper.absolutePos(PLAIN), engines("cylinder"));
		build(helper.getLevel(), helper.absolutePos(RACING), engines("cylinder"), RacingAddon.RACING_FLYWHEEL.get());
		helper.assertTrue(helper.getLevel()
			.getBlockEntity(helper.absolutePos(RACING.offset(0, 2, 2))) != null, "the racing flywheel has a block entity");
		float[] early = new float[2];
		helper.onEachTick(() -> {
			Optional<EngineView> plain = view(helper, PLAIN);
			Optional<EngineView> heavy = view(helper, RACING);
			if (plain.isEmpty() || heavy.isEmpty())
				return;
			if (helper.getTick() == 60) {
				early[0] = plain.get()
					.rpm();
				early[1] = heavy.get()
					.rpm();
			}
			if (helper.getTick() < 300)
				return;
			helper.assertTrue(heavy.get()
				.status() == EngineStatus.RUNNING, "the engine with the racing flywheel should be running: " + heavy.get());
			helper.assertTrue(early[0] > 0 && early[1] < early[0], "a flywheel's mass slows the revs: " + early[1] + " rpm with it at tick 60, "
				+ early[0] + " without");
			helper.succeed();
		});
	}

	@GameTest(template = "empty_large", timeoutTicks = 700)
	public static void anEngineWithTheRacingCylinderRunsAndShowsThePartInItsNumbers(GameTestHelper helper) {
		build(helper.getLevel(), helper.absolutePos(PLAIN), engines("cylinder"));
		build(helper.getLevel(), helper.absolutePos(RACING), RacingAddon.RACING_CYLINDER.get());
		helper.assertTrue(helper.getLevel()
			.getBlockEntity(helper.absolutePos(RACING.offset(2, 3, 2))) != null, "the racing cylinder has a block entity, so the engine can use it");
		// Both engines have run for a while and are working: only then is what the bore can deliver worth comparing
		helper.onEachTick(() -> {
			Optional<EngineView> plain = view(helper, PLAIN);
			Optional<EngineView> racing = view(helper, RACING);
			if (helper.getTick() < 300 || plain.isEmpty() || racing.isEmpty())
				return;
			helper.assertTrue(racing.get()
				.cylinders() == 1, "the racing cylinder counts as one cylinder: " + racing.get()
					.cylinders());
			helper.assertTrue(plain.get()
				.status() == EngineStatus.RUNNING, "the plain engine should be running: " + plain.get());
			helper.assertTrue(racing.get()
				.status() == EngineStatus.RUNNING, "the engine with the racing cylinder should be running: " + racing.get());
			// The part's higher compression and quicker burn let the bore do more work than the mod's own
			helper.assertTrue(racing.get()
				.availableTorque() > plain.get()
					.availableTorque(), "the racing bore should be able to deliver more than the plain one: " + racing.get()
						.availableTorque() + " against " + plain.get()
							.availableTorque());
			helper.succeed();
		});
	}
}
