package com.createengines.compat.jade;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.createengines.api.EngineStatus;
import com.createengines.api.EngineView;
import com.createengines.registry.CELang;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * What the Jade overlay says about an engine, kept apart from Jade so it can be tested with no Jade loaded: the server writes
 * an {@link EngineView} into a tag, the client reads the tag back into lines. It uses the same words as the goggles.
 * <p>
 * The tag is read on the client from whatever the server sent, so nothing in it is trusted: a missing, unknown or out of range
 * value reads as "no engine" or is clamped, and never throws.
 */
public final class EngineTooltip {

	/** The key the engine's tag sits under in Jade's server data. */
	public static final String KEY = "CreateEnginesEngine";

	private EngineTooltip() {
	}

	public static CompoundTag write(EngineView view) {
		CompoundTag tag = new CompoundTag();
		tag.putString("Status", view.status()
			.name()
			.toLowerCase(Locale.ROOT));
		tag.putFloat("Rpm", view.rpm());
		tag.putInt("Cylinders", view.cylinders());
		tag.putInt("Firing", view.firing());
		tag.putFloat("Oil", view.oilCondition());
		tag.putFloat("Boost", view.boost());
		tag.putString("Health", view.health());
		return tag;
	}

	/** The lines for the engine in {@code data} (Jade's whole server data tag), empty when it holds none. */
	public static List<Component> lines(@Nullable CompoundTag data) {
		List<Component> lines = new ArrayList<>();
		if (data == null || !data.contains(KEY, 10))
			return lines;
		CompoundTag tag = data.getCompound(KEY);
		EngineStatus status = status(tag.getString("Status"));
		if (status == null)
			return lines;
		String name = status.name()
			.toLowerCase(Locale.ROOT);
		lines.add(CELang.translate("goggles.engine")
			.component()
			.copy()
			.append(": ")
			.append(CELang.translate("goggles.state." + name)
				.component())
			.withStyle(status == EngineStatus.RUNNING ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		float rpm = finite(tag.getFloat("Rpm"));
		if (rpm != 0)
			lines.add(CELang.translate("goggles.rpm", Math.round(Math.abs(rpm)))
				.component());
		float boost = finite(tag.getFloat("Boost"));
		if (boost > 0)
			lines.add(CELang.translate("jade.engine.boost", String.format(Locale.ROOT, "%.1f", boost))
				.component());
		String health = tag.getString("Health");
		if (health.equals("rough") || health.equals("failing"))
			lines.add(CELang.translate("goggles.health.line", CELang.translate("goggles.health." + health)
				.component())
				.component());
		return lines;
	}

	private static @Nullable EngineStatus status(String name) {
		for (EngineStatus status : EngineStatus.values())
			if (status.name()
				.toLowerCase(Locale.ROOT)
				.equals(name))
				return status;
		return null;
	}

	private static float finite(float value) {
		return Float.isFinite(value) ? value : 0;
	}
}
