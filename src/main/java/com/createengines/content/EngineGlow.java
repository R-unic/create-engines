package com.createengines.content;

import com.createengines.content.head.CylinderHeadBlock;
import com.createengines.content.rotor.RotorHousingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The faint light a burning engine gives off, from the heads and rotor housings where the charge burns. It is the
 * vanilla {@code lit} property, switched by the engine controller while the engine is firing.
 */
public final class EngineGlow {

	/** Light level of a burning head or housing: a dim glow, a little under a torch. */
	public static final int LEVEL = 5;

	private EngineGlow() {
	}

	/** The light level of a head or housing in this state, for the block's properties. */
	public static int light(BlockState state) {
		return state.getValue(BlockStateProperties.LIT) ? LEVEL : 0;
	}

	/** Lights or darkens the head or housing at {@code pos}, if there is one in a loaded chunk, and does nothing if it already is. */
	public static void set(Level level, BlockPos pos, boolean lit) {
		if (!level.isLoaded(pos))
			return;
		BlockState state = level.getBlockState(pos);
		if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit
			&& (state.getBlock() instanceof CylinderHeadBlock || state.getBlock() instanceof RotorHousingBlock))
			level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
	}

	/**
	 * Whether the glow is the only thing that differs between two states of the same block. Switching it must not make
	 * the engine be scanned again, or the scan would switch it again.
	 */
	public static boolean onlyGlowChanged(BlockState state, BlockState other) {
		return other.is(state.getBlock()) && other.setValue(BlockStateProperties.LIT, state.getValue(BlockStateProperties.LIT)) == state;
	}
}
