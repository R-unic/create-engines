package com.createengines.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** Fires when the mod reports a {@link Milestone} by name to a player. One trigger type serves every such advancement. */
public class EventTrigger extends SimpleCriterionTrigger<EventTrigger.Instance> {

	public record Instance(Optional<ContextAwarePredicate> player, String event) implements SimpleInstance {

		public static final Codec<Instance> CODEC = RecordCodecBuilder.create(builder -> builder
			.group(ContextAwarePredicate.CODEC.optionalFieldOf("player")
				.forGetter(Instance::player), Codec.STRING.fieldOf("event")
					.forGetter(Instance::event))
			.apply(builder, Instance::new));
	}

	@Override
	public Codec<Instance> codec() {
		return Instance.CODEC;
	}

	public void trigger(ServerPlayer player, Milestone milestone) {
		trigger(player, instance -> instance.event()
			.equals(milestone.id()));
	}

	/** The criterion an advancement uses to wait for {@code milestone}. */
	public Criterion<Instance> waitingFor(Milestone milestone) {
		return createCriterion(new Instance(Optional.empty(), milestone.id()));
	}
}
