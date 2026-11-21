package example.racing;

import com.createengines.api.PartBlocks;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A worked example of a <b>part addon</b> for Create: Engine Engineer: one new cylinder block, the Racing Cylinder, that adds
 * a part of its own to the engine it belongs to.
 *
 * <p>The three things an addon does for a part, and where each is:
 * <ol>
 * <li>Makes the block with {@link PartBlocks#cylinder}, so the engine scanner, the goggles and Create's wrench treat it like
 * any other cylinder, registers it with the game as any block, and tells the mod about it with {@link PartBlocks#register}.</li>
 * <li>Names its part effect in the {@code part_effect} block data map
 * ({@code data/create_engines/data_maps/block/part_effect.json}).</li>
 * <li>Ships the effect itself as {@code data/racing_addon/part_effects/racing_bore.json}: the same keys and ranges as the
 * mod's own bore, head, piston and injector files.</li>
 * </ol>
 * Nothing here changes a block of the mod's, so an engine built from the mod's parts alone runs exactly as it did. The only
 * classes of the mod this addon names are in {@code com.createengines.api}, which is what keeps it working between versions.
 */
@Mod(RacingAddon.ID)
public class RacingAddon {

	public static final String ID = "racing_addon";

	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);

	public static final DeferredBlock<Block> RACING_CYLINDER = BLOCKS.register("racing_cylinder",
		() -> PartBlocks.cylinder(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED)
			.strength(3.5f, 6)
			.requiresCorrectToolForDrops()
			.noOcclusion()));

	public static final DeferredItem<BlockItem> RACING_CYLINDER_ITEM = ITEMS.register("racing_cylinder",
		() -> new BlockItem(RACING_CYLINDER.get(), new Item.Properties()));

	public RacingAddon(IEventBus modBus) {
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		PartBlocks.register(modBus, RACING_CYLINDER);
	}
}
