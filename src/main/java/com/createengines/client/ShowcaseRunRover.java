package com.createengines.client;

import static com.createengines.client.ShowcaseRun.*;
import static com.createengines.client.ShowcaseRunWorks.*;
import static com.createengines.client.ShowcaseRunBuild.*;

import com.createengines.CreateEngines;
import com.createengines.content.tool.BankBuilderItem;
import com.simibubi.create.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The trailer's wheeled car (Create Aeronautics and Sable runs). Part of {@link ShowcaseRun}, and like it kept out of the jar. */
final class ShowcaseRunRover {

	private ShowcaseRunRover() {
	}

	/**
	 * The wheeled engine: a diesel inline six whose crank line runs out of both ends into a gearbox, a second gearbox below it, and
	 * an axle across the car with an Offroad wheel mount and tire at each end. Create Aeronautics' Offroad wheels take their drive from
	 * the shaft behind the mount, so the pad's dais is taken away and the car stands on its four wheels once {@link #liftOff} makes it
	 * a moving structure.
	 */
	static void rover(ServerLevel level, ServerPlayer player, BlockPos at) {
		bank(level, player, at, BankBuilderItem.Layout.INLINE, 8, new Kit(Fuel.DIESEL, 1, true, 0, false, false, false), STAGE_ALL, true);
		for (int x = -1; x <= 8 + 1 + 5; x++)
			for (int z = CZ - 2; z <= CZ + 2; z++)
				level.setBlock(at.offset(x, 0, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		// The east axle takes the flywheel's end; the west one the crank's near end
		axle(level, at, 8 + 1 + 6, 8 + 1 + 5, 1);
		// The west pair only rolls, on a frame of andesite casing from the starter's motor (blocks that touch nothing are split off
		// into structures of their own when the car is lifted)
		BlockState frame = AllBlocks.ANDESITE_CASING.getDefaultState();
		for (int x = -1; x <= 0; x++)
			set(level, at, x, 1, CZ, frame);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, -1, 1, z, frame);
		wheel(level, at.offset(-1, 1, CZ - 2), Direction.NORTH);
		wheel(level, at.offset(-1, 1, CZ + 2), Direction.SOUTH);
		body(level, at);
		clearDrops(level);
	}

	/**
	 * What makes the car a rover and not an engine on wheels: brass frame rails with crossmembers from the rear axle to the front one,
	 * running boards beside the engine, a closed cab over the rear axle (two red seats, a dash, glass all round, a roof with a lamp and an
	 * aerial), a cargo bed behind it with barrels, and a bumper with a headlamp at each corner. Every block touches the frame, since a
	 * block that touches nothing is split off into a structure of its own when the car is lifted, and none sits where the engine's
	 * own parts are (the crank line, its starter column and the pipes all keep to the engine's side of the line y = 2 / z = 4..6).
	 */
	static void body(ServerLevel level, BlockPos at) {
		BlockState brass = AllBlocks.BRASS_CASING.getDefaultState();
		BlockState andesite = AllBlocks.ANDESITE_CASING.getDefaultState();
		BlockState bars = AllBlocks.ANDESITE_BARS.getDefaultState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		// The frame: a rail down each side, joined by crossmembers (the starter's column and the far journal's legs stand at z = 5)
		for (int x = 0; x <= 14; x++) {
			set(level, at, x, 1, CZ - 2, brass);
			set(level, at, x, 1, CZ + 2, brass);
		}
		for (int x : new int[] { 4, 7, 12 })
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 1, z, andesite);
		// Running boards along the engine, clear of the tires at either end
		BlockState board = Blocks.SPRUCE_SLAB.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
		for (int x = 3; x <= 11; x++) {
			set(level, at, x, 2, CZ - 2, board);
			set(level, at, x, 2, CZ + 2, board);
		}

		// The rear: a deck from the cab to the tailgate
		for (int x = -6; x <= -2; x++)
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 1, z, x >= -3 ? brass : andesite);
		// The cab: corner posts, a dash across the front, glass on all four sides, a roof
		for (int y = 2; y <= 4; y++)
			for (int x : new int[] { -3, -1 })
				for (int z : new int[] { CZ - 1, CZ + 1 })
					set(level, at, x, y, z, andesite);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, -1, 2, z, brass);
		for (int y = 3; y <= 4; y++) {
			set(level, at, -1, y, CZ, glass);
			set(level, at, -3, y, CZ, glass);
			set(level, at, -2, y, CZ - 1, glass);
			set(level, at, -2, y, CZ + 1, glass);
		}
		for (int x = -3; x <= -1; x++)
			for (int z = CZ - 1; z <= CZ + 1; z++)
				set(level, at, x, 5, z, brass);
		set(level, at, -2, 2, CZ - 1, AllBlocks.SEATS.get(DyeColor.RED)
			.getDefaultState());
		set(level, at, -2, 2, CZ + 1, AllBlocks.SEATS.get(DyeColor.RED)
			.getDefaultState());
		// On the roof: a lamp at the front, a rack of bars round it, an aerial at the back
		set(level, at, -1, 6, CZ, Blocks.LANTERN.defaultBlockState());
		for (int x = -3; x <= -2; x++) {
			set(level, at, x, 6, CZ - 1, bars);
			set(level, at, x, 6, CZ + 1, bars);
		}
		set(level, at, -3, 6, CZ, bars);
		set(level, at, -2, 6, CZ, Blocks.BARREL.defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.UP));
		set(level, at, -3, 7, CZ + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
		set(level, at, -3, 8, CZ + 1, Blocks.END_ROD.defaultBlockState());

		// The bed: bars along both sides and across the tailgate, drums standing on the deck
		for (int x = -6; x <= -4; x++) {
			set(level, at, x, 2, CZ - 1, bars);
			set(level, at, x, 2, CZ + 1, bars);
		}
		set(level, at, -6, 2, CZ, bars);
		for (int x = -5; x <= -4; x++)
			set(level, at, x, 2, CZ, Blocks.BARREL.defaultBlockState()
				.setValue(BlockStateProperties.FACING, Direction.UP));
		set(level, at, -5, 3, CZ, Blocks.BARREL.defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.UP));

		// The nose: a bumper of bars between a brass post and headlamp at each corner
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, 16, 1, z, bars);
		for (int z : new int[] { CZ - 2, CZ + 2 }) {
			for (int y = 1; y <= 2; y++)
				set(level, at, 16, y, z, brass);
			set(level, at, 16, 3, z, Blocks.LANTERN.defaultBlockState());
		}
		// A grille of bars between the posts under a brass brow, with a lamp in the middle of it and a spotlamp on the brow
		for (int z = CZ - 1; z <= CZ + 1; z++) {
			set(level, at, 16, 2, z, bars);
			set(level, at, 16, 3, z, brass);
		}
		set(level, at, 16, 4, CZ, Blocks.LANTERN.defaultBlockState());
		set(level, at, 16, 4, CZ - 1, Blocks.END_ROD.defaultBlockState());
		set(level, at, 16, 4, CZ + 1, Blocks.END_ROD.defaultBlockState());
		// A plough ahead of the bumper: a wedge of polished andesite stepping down to the ground
		BlockState plough = Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
		for (int z = CZ - 1; z <= CZ + 1; z++)
			set(level, at, 17, 1, z, plough);
		set(level, at, 18, 1, CZ, plough);
	}

	/**
	 * The driven wheel: from the crank line at {@code lineEnd} a shaft runs to a gearbox at {@code x}, down a shaft into a second
	 * gearbox at height 1 and out to one wheel mount on the north side, straight, with no reduction (one driven mount at full speed
	 * is within a turbocharged eight's stress; two are not). The south side has a mount with a tire and no drive, held on by a casing
	 * frame, so the car rolls on a pair.
	 */
	static void axle(ServerLevel level, BlockPos at, int x, int lineEnd, int step) {
		for (int along = lineEnd; along != x; along += step)
			set(level, at, along, CY, CZ, axisX(AllBlocks.SHAFT.getDefaultState()));
		set(level, at, x, CY, CZ, AllBlocks.GEARBOX.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		set(level, at, x, CY - 1, CZ, AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Y));
		set(level, at, x, 1, CZ, AllBlocks.GEARBOX.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.X));
		set(level, at, x, 1, CZ - 1, AllBlocks.SHAFT.getDefaultState()
			.setValue(BlockStateProperties.AXIS, Axis.Z));
		BlockState frame = AllBlocks.ANDESITE_CASING.getDefaultState();
		set(level, at, x, 1, CZ + 1, frame);
		wheel(level, at.offset(x, 1, CZ - 2), Direction.NORTH);
		wheel(level, at.offset(x, 1, CZ + 2), Direction.SOUTH);
	}

	/** Picks up every dropped item round the wheeled car's pad and the road it drives (no drops lie in the shots). */
	static void clearDrops(ServerLevel level) {
		BlockPos at = pad(AERO_BAY);
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(at.getX() - 20, at.getY() - 6, at.getZ() - 20, at.getX() + 110, at.getY() + 20, at.getZ() + 30)))
			item.discard();
	}

	/** An Offroad wheel mount facing {@code side}, with a tire on it. */
	static void wheel(ServerLevel level, BlockPos mountAt, Direction side) {
		BlockState state = block("offroad:wheel_mount", Blocks.AIR).defaultBlockState();
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, side);
		else
			CreateEngines.LOGGER.warn("Showcase: the wheel mount has no horizontal facing; blockstate {}", state);
		level.setBlock(mountAt, state, Block.UPDATE_ALL);
		putTire(level, mountAt, BuiltInRegistries.ITEM.get(ResourceLocation.parse("offroad:tire")));
	}

	/** Puts a tire in a wheel mount's one slot, by reflection: Offroad is not something this mod is built against. */
	static void putTire(ServerLevel level, BlockPos mountAt, net.minecraft.world.item.Item tire) {
		try {
			var be = level.getBlockEntity(mountAt);
			Object inventory = be.getClass()
				.getMethod("getInventory")
				.invoke(be);
			inventory.getClass()
				.getMethod("setItem", int.class, ItemStack.class)
				.invoke(inventory, 0, new ItemStack(tire));
		} catch (ReflectiveOperationException | RuntimeException e) {
			CreateEngines.LOGGER.warn("Showcase: no tire went on the wheel mount at {}", mountAt, e);
		}
	}

	/** Lifts the wheeled car's pad into one moving structure, where it stands, and keeps its heads fed in their new places. */
	static void liftOff() {
		server(server -> {
			ServerLevel level = server.overworld();
			BlockPos at = pad(AERO_BAY);
			clearDrops(level);
			var heads = ShowcaseRunAero.assemble(level, at.offset(-7, 0, CZ - 4), at.offset(18, CY + 6, CZ + 4));
			aeroStart = ShowcaseRunAero.position();
			TANKS.removeIf(tank -> tank.pos().getX() >= at.getX() - 7 && tank.pos().getX() <= at.getX() + 18
				&& tank.pos().getZ() >= at.getZ() + CZ - 4 && tank.pos().getZ() <= at.getZ() + CZ + 4 && tank.pos().getY() >= at.getY());
			for (BlockPos head : heads)
				TANKS.add(new Tank(head, Fuel.DIESEL));
		});
	}
}
