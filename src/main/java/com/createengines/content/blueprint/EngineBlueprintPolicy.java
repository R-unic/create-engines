package com.createengines.content.blueprint;

import org.jetbrains.annotations.Nullable;

import com.createengines.CreateEngines;
import com.createengines.engine.EngineProviders;
import com.createengines.engine.blueprint.BlueprintPolicy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * What a server accepts in a blueprint: blocks of this mod and of Create, and blocks a pack or addon has claimed by registering an
 * engine source provider that watches them (never any other mod's, and never vanilla's unless claimed), a state
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

	/** Whether this block may be in a blueprint: of this mod or Create, or one a provider claims. */
	public static boolean allows(Block block) {
		return allowsNamespace(BuiltInRegistries.BLOCK.getKey(block)
			.getNamespace()) || EngineProviders.claims(block);
	}

	/** The registered block that may be in a blueprint with that id, or null. */
	public static @Nullable Block block(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null)
			return null;
		Block found = BuiltInRegistries.BLOCK.getOptional(location)
			.orElse(null);
		return found != null && allows(found) ? found : null;
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
