package com.createengines.content.refinery;

import com.createengines.content.PartShapes;
import com.createengines.engine.sim.ColumnShape.Part;
import com.createengines.engine.sim.RefiningColumn.Limit;
import com.simibubi.create.content.equipment.wrench.IWrenchable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The chimney a refinery burns its waste gas from. It does nothing but look the part: stood on a Condenser (or on
 * another stack that is), its flame burns only while that column is making product.
 */
public class FlareStackBlock extends Block implements IWrenchable {

	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	private static final int INTERVAL = 10;
	private static final VoxelShape SHAPE = PartShapes.FLARE_STACK;

	public FlareStackBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!level.isClientSide && !oldState.is(this))
			level.scheduleTick(pos, this, 1);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean burning = burning(level, pos);
		if (burning != state.getValue(LIT))
			level.setBlock(pos, state.setValue(LIT, burning), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, INTERVAL);
	}

	/** Whether the column this stack stands on is making product. Looks down through other stacks, so they can be piled. */
	static boolean burning(Level level, BlockPos pos) {
		BlockPos below = pos.below();
		int guard = 16;
		while (guard-- > 0 && level.isLoaded(below) && level.getBlockState(below)
			.getBlock() instanceof FlareStackBlock)
			below = below.below();
		if (!level.isLoaded(below) || RefiningColumns.partAt(level, below) != Part.CONDENSER
			|| !(level.getBlockEntity(below) instanceof ColumnPortBlockEntity port))
			return false;
		ColumnPortBlockEntity controller = port.controller();
		return controller != null && controller.limit() == Limit.NONE && controller.rate() > 0;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT))
			return;
		if (random.nextInt(2) == 0)
			level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.15,
				pos.getY() + 1.0, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.15, 0, 0.03, 0);
		if (random.nextInt(20) == 0)
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
				com.createengines.registry.CESounds.FLARE_WHOOSH.get(), net.minecraft.sounds.SoundSource.BLOCKS,
				0.3f + random.nextFloat() * 0.15f, 0.9f + random.nextFloat() * 0.2f, false);
		if (random.nextInt(6) == 0)
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, 0.05, 0);
	}
}
