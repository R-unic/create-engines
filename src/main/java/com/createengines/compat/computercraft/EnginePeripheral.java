package com.createengines.compat.computercraft;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createengines.api.EngineApi;
import com.createengines.api.EngineView;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What a ComputerCraft computer sees of an engine when it is wired to, or touching, one of its blocks (a crankshaft, a
 * cylinder, a cylinder head or a rotor housing). It is read-only, like {@link EngineApi} that it is built on: a computer can
 * watch an engine and react (cut the fuel from outside, ring a bell on {@code failing}) but cannot change one.
 * <p>
 * Every method answers for the whole engine the block belongs to, and returns {@code nil} when the block is not part of a
 * built engine or the engine has not reported yet. They run on the server thread, as all of a world's reads must.
 */
public final class EnginePeripheral implements IPeripheral {

	/** The peripheral type a computer sees: {@code peripheral.getType(name)} and {@code peripheral.find("create_engines_engine")}. */
	public static final String TYPE = "create_engines_engine";

	private final BlockEntity block;

	public EnginePeripheral(BlockEntity block) {
		this.block = block;
	}

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public Object getTarget() {
		return block;
	}

	@Override
	public boolean equals(@Nullable IPeripheral other) {
		return other instanceof EnginePeripheral peripheral && peripheral.block == block;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof IPeripheral peripheral && equals(peripheral);
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(block);
	}

	private @Nullable EngineView view() {
		Level level = block.getLevel();
		BlockPos pos = block.getBlockPos();
		if (level == null || block.isRemoved())
			return null;
		return EngineApi.viewAt(level, pos)
			.orElse(null);
	}

	/** Everything below in one table, or nil. */
	@LuaFunction(mainThread = true)
	public @Nullable Map<String, Object> getEngine() {
		return table(view());
	}

	/**
	 * The engine's state, lower case: {@code stalled}, {@code cranking}, {@code coasting}, {@code running}, {@code overloaded},
	 * {@code incomplete} or {@code broken} (the names of {@link com.createengines.api.EngineStatus}).
	 */
	@LuaFunction(mainThread = true)
	public @Nullable String getStatus() {
		EngineView view = view();
		return view == null ? null : view.status()
			.name()
			.toLowerCase(Locale.ROOT);
	}

	@LuaFunction(mainThread = true)
	public @Nullable Float getRpm() {
		EngineView view = view();
		return view == null ? null : view.rpm();
	}

	/** Stress capacity per RPM the engine could deliver at full fuel. */
	@LuaFunction(mainThread = true)
	public @Nullable Float getAvailableTorque() {
		EngineView view = view();
		return view == null ? null : view.availableTorque();
	}

	/** Stress per RPM the network is asking for. */
	@LuaFunction(mainThread = true)
	public @Nullable Float getLoadTorque() {
		EngineView view = view();
		return view == null ? null : view.loadTorque();
	}

	/** Working cylinders (or rotor chambers). */
	@LuaFunction(mainThread = true)
	public @Nullable Integer getCylinders() {
		EngineView view = view();
		return view == null ? null : view.cylinders();
	}

	/** How many cylinders fired in the last cycle. */
	@LuaFunction(mainThread = true)
	public @Nullable Integer getFiring() {
		EngineView view = view();
		return view == null ? null : view.firing();
	}

	/** 0 is ambient, 0.7 a warm engine on a steady load. */
	@LuaFunction(mainThread = true)
	public @Nullable Float getTemperature() {
		EngineView view = view();
		return view == null ? null : view.temperature();
	}

	/** 0 spent, 1 new. */
	@LuaFunction(mainThread = true)
	public @Nullable Float getOilCondition() {
		EngineView view = view();
		return view == null ? null : view.oilCondition();
	}

	/** The highest boost of any turbocharger or supercharger on the engine, 0 with none. */
	@LuaFunction(mainThread = true)
	public @Nullable Float getBoost() {
		EngineView view = view();
		return view == null ? null : view.boost();
	}

	/** {@code healthy}, {@code rough} or {@code failing}. */
	@LuaFunction(mainThread = true)
	public @Nullable String getHealth() {
		EngineView view = view();
		return view == null ? null : view.health();
	}

	/** The one thing most responsible for the health, a lower case name, or {@code none}. */
	@LuaFunction(mainThread = true)
	public @Nullable String getCause() {
		EngineView view = view();
		return view == null ? null : view.cause();
	}

	/** The view as a Lua table's contents. Package-visible so a test can compare it with the view it came from. */
	static @Nullable Map<String, Object> table(@Nullable EngineView view) {
		if (view == null)
			return null;
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("status", view.status()
			.name()
			.toLowerCase(Locale.ROOT));
		map.put("rpm", view.rpm());
		map.put("availableTorque", view.availableTorque());
		map.put("loadTorque", view.loadTorque());
		map.put("cylinders", view.cylinders());
		map.put("firing", view.firing());
		map.put("temperature", view.temperature());
		map.put("oilCondition", view.oilCondition());
		map.put("boost", view.boost());
		map.put("health", view.health());
		map.put("cause", view.cause());
		return map;
	}
}
