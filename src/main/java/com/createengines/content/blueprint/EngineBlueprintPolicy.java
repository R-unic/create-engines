package com.createengines.content.blueprint;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.blueprint.BlueprintPolicy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * What a server accepts in a blueprint: blocks of this mod and of Create (never another mod's, and never vanilla's), a state
 * property only if the block really has it and takes that value, and a dial only if {@link EngineDials} lists it for that
 * block and the value is inside its range.
 */
public final class EngineBlueprintPolicy implements BlueprintPolicy {

	public static final EngineBlueprintPolicy INSTANCE = new EngineBlueprintPolicy();

	private EngineBlueprintPolicy() {
	}

	/** Create's namespace: its blocks (shafts, cogwheels, gearboxes, belts...) are allowed beside this mod's. */
	public static final String CREATE = "create";

	/** Whether blocks of this namespace may be in a blueprint: this mod's and Create's. */
	public static boolean allowsNamespace(String namespace) {
		return namespace.equals(CreateEngines.MOD_ID) || namespace.equals(CREATE);
	}

	/** The registered block of this mod or of Create with that id, or null. */
	public static @Nullable Block block(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null || !allowsNamespace(location.getNamespace()))
			return null;
		return BuiltInRegistries.BLOCK.getOptional(location)
			.orElse(null);
	}

	@Override
	public boolean allowsBlock(String block) {
		return block(block) != null;
	}

	@Override
	public boolean allowsState(String block, String property, String value) {
		Block found = block(block);
		if (found == null)
			return false;
		Property<?> p = found.getStateDefinition()
			.getProperty(property);
		return p != null && p.getValue(value)
			.isPresent();
	}

	@Override
	public int dialMin(String block, String dial) {
		EngineDials.Dial found = EngineDials.find(block, dial);
		return found == null ? Integer.MAX_VALUE : found.min();
	}

	@Override
	public int dialMax(String block, String dial) {
		EngineDials.Dial found = EngineDials.find(block, dial);
		return found == null ? Integer.MIN_VALUE : found.max();
	}
}
