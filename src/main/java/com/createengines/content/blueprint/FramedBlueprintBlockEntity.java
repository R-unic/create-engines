package com.createengines.content.blueprint;

import org.jetbrains.annotations.Nullable;

import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.blueprint.FrameSheet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blueprint hung in a frame. It is stored as the same item data a blueprint item carries, so nothing is lost on the
 * way in or out, and decoded once (on the server and the client) for the drawing. Saved data that does not read back is
 * treated as an empty frame, never an error.
 */
public class FramedBlueprintBlockEntity extends BlockEntity {

	private @Nullable RecordedBlueprint plan;
	private @Nullable Blueprint drawing;
	private static final int SHEET_REFRESH_TICKS = 20;
	private FrameSheet.Sheet sheet;
	private long sheetAt;

	public FramedBlueprintBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** What is hung in the frame, or null if it is empty. */
	public @Nullable RecordedBlueprint plan() {
		return plan;
	}

	/** The decoded blueprint, for drawing. Null if the frame is empty or what it holds cannot be read. */
	public @Nullable Blueprint drawing() {
		return drawing;
	}

	/**
	 * The sheet of connected frames this one belongs to, looked up again at most once a second (neighbours changing are not
	 * announced to a client). A frame on its own is a sheet of one.
	 */
	public FrameSheet.Sheet sheet() {
		long now = level == null ? 0 : level.getGameTime();
		if (sheet == null || now - sheetAt >= SHEET_REFRESH_TICKS || now < sheetAt)
			refreshSheet();
		return sheet;
	}

	/** Works the sheet out now from the frames around this one. */
	public FrameSheet.Sheet refreshSheet() {
		sheetAt = level == null ? 0 : level.getGameTime();
		if (level == null || !(getBlockState().getBlock() instanceof FramedBlueprintBlock)) {
			sheet = new FrameSheet.Sheet(0, 0, 1, 1, drawing == null ? null : new FrameSheet.Cell(0, 0));
			return sheet;
		}
		Direction facing = getBlockState().getValue(FramedBlueprintBlock.FACING);
		Direction right = FramedBlueprintBlock.paperRight(facing);
		Direction down = FramedBlueprintBlock.paperDown(facing);
		BlockPos origin = worldPosition;
		Level world = level;
		sheet = FrameSheet.find(cell -> {
			BlockState there = world.getBlockState(cell(origin, right, down, cell));
			return there.getBlock() instanceof FramedBlueprintBlock && there.getValue(FramedBlueprintBlock.FACING) == facing;
		}, cell -> world.getBlockEntity(cell(origin, right, down, cell)) instanceof FramedBlueprintBlockEntity other
			&& other.drawing() != null);
		return sheet;
	}

	/** The block {@code cell} frames to the right of and below {@code origin} on the paper's own axes. */
	public static BlockPos cell(BlockPos origin, Direction right, Direction down, FrameSheet.Cell cell) {
		return origin.relative(right, cell.u())
			.relative(down, cell.v());
	}

	public void hang(RecordedBlueprint plan) {
		set(plan);
		changed();
	}

	/** Empties the frame and gives back the blueprint item it held. */
	public ItemStack take() {
		ItemStack out = plan == null ? ItemStack.EMPTY : FramedBlueprintBlock.blueprintItem(plan);
		set(null);
		changed();
		return out;
	}

	private void set(@Nullable RecordedBlueprint plan) {
		this.plan = plan;
		Blueprint decoded = null;
		if (plan != null)
			try {
				decoded = BlueprintCodec.decode(plan.text());
			} catch (BlueprintException e) {
				// shown as an empty frame; the item still comes back out as it went in
			}
		this.drawing = decoded;
	}

	private void changed() {
		setChanged();
		if (level != null)
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (plan != null)
			RecordedBlueprint.CODEC.encodeStart(NbtOps.INSTANCE, plan)
				.result()
				.ifPresent(encoded -> tag.put("Plan", encoded));
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		RecordedBlueprint read = null;
		if (tag.contains("Plan"))
			read = RecordedBlueprint.CODEC.parse(NbtOps.INSTANCE, tag.get("Plan"))
				.result()
				.orElse(null);
		set(read);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
