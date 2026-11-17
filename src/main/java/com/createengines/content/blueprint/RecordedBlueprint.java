package com.createengines.content.blueprint;

import com.createengines.engine.blueprint.Blueprint.Limits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a blueprint item holds once an engine is recorded in it: the blueprint as its pasteable string (so the saved item
 * carries the format version and a later release reads it), plus the two things a tooltip wants without decoding it.
 * The string is bounded on every path (saved data, network, creative-mode item grabs), and a stack that carries a
 * hostile one is still checked by the decoder and the rules before anything is built from it.
 *
 * @param text  the blueprint as written by {@code BlueprintCodec.encode}
 * @param name  the blueprint's name
 * @param parts how many blocks it has
 */
public record RecordedBlueprint(String text, String name, int parts) {

	public static final Codec<RecordedBlueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.string(0, Limits.MAX_TEXT)
			.fieldOf("text")
			.forGetter(RecordedBlueprint::text),
		Codec.string(0, Limits.MAX_NAME)
			.fieldOf("name")
			.forGetter(RecordedBlueprint::name),
		Codec.intRange(0, Limits.MAX_PARTS)
			.fieldOf("parts")
			.forGetter(RecordedBlueprint::parts))
		.apply(i, RecordedBlueprint::new));

	public static final StreamCodec<ByteBuf, RecordedBlueprint> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(Limits.MAX_TEXT), RecordedBlueprint::text,
		ByteBufCodecs.stringUtf8(Limits.MAX_NAME), RecordedBlueprint::name,
		ByteBufCodecs.VAR_INT, RecordedBlueprint::parts,
		RecordedBlueprint::new);
}
