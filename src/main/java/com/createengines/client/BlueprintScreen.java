package com.createengines.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.content.display.EngineReadout;
import com.createengines.content.plate.MakerPlateBlockEntity;
import com.createengines.engine.blueprint.Blueprint;
import com.createengines.engine.blueprint.BlueprintCodec;
import com.createengines.engine.blueprint.BlueprintException;
import com.createengines.engine.sim.PlateFigures;
import com.createengines.registry.CELang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The drafting sheet: a recorded blueprint drawn as the engine seen from above, a list of its parts, and the figures
 * from the Maker's Plate it was recorded with. Everything on it comes from decoding the blueprint string, so a sheet
 * shows exactly what applying it would work from. Client only.
 */
public class BlueprintScreen extends Screen {

	private static final int WIDTH = 280;
	private static final int HEIGHT = 190;
	private static final int PAPER = 0xFF2F5894;
	private static final int PAPER_GRID = 0xFF3A68A8;
	private static final int EDGE = 0xFF16294A;
	private static final int INK = 0xFFD7E6FA;
	private static final int INK_DIM = 0xFF8FB3E0;
	private static final int DRAWING = 118;

	private final Blueprint blueprint;
	private final boolean damaged;
	private final List<String> lines = new ArrayList<>();

	public static void open(RecordedBlueprint recorded) {
		Minecraft.getInstance()
			.setScreen(new BlueprintScreen(recorded));
	}

	/** A sheet that is not shown yet, for the client audit to draw. */
	public static BlueprintScreen create(RecordedBlueprint recorded) {
		return new BlueprintScreen(recorded);
	}

	private BlueprintScreen(RecordedBlueprint recorded) {
		super(Component.literal(recorded.name()));
		Blueprint read = null;
		try {
			read = BlueprintCodec.decode(recorded.text());
		} catch (BlueprintException e) {
			// shown as a damaged sheet
		}
		blueprint = read;
		damaged = read == null;
		if (read != null)
			for (Map.Entry<String, Integer> entry : counts(read).entrySet())
				lines.add(entry.getValue() + " x " + nameOf(entry.getKey()));
	}

	/** Parts by block, most numerous first (then by id, so the order never wobbles). */
	static Map<String, Integer> counts(Blueprint blueprint) {
		Map<String, Integer> counts = new TreeMap<>();
		for (Blueprint.Part part : blueprint.parts())
			counts.merge(part.block(), 1, Integer::sum);
		List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
		sorted.sort((a, b) -> a.getValue()
			.equals(b.getValue()) ? a.getKey()
				.compareTo(b.getKey()) : Integer.compare(b.getValue(), a.getValue()));
		Map<String, Integer> ordered = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : sorted)
			ordered.put(entry.getKey(), entry.getValue());
		return ordered;
	}

	private static String nameOf(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		if (location == null)
			return id;
		return BuiltInRegistries.BLOCK.getOptional(location)
			.map(block -> block.getName()
				.getString())
			.orElse(id);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The colour a block is drawn in: crankshaft steel, cylinder brass, cams green, intakes blue, exhaust red. */
	public static int colourOf(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		if (path.startsWith("crank") || path.startsWith("rotor_housing") || path.startsWith("cast_flywheel") || path.startsWith("dual_mass_flywheel"))
			return 0xFFC3CCD6;
		if (path.contains("cylinder_head"))
			return 0xFFE6E2D2;
		if (path.contains("cylinder"))
			return 0xFFE0A840;
		if (path.startsWith("cam"))
			return 0xFF72B872;
		if (path.contains("intake") || path.contains("throttle") || path.contains("maf") || path.contains("supercharger"))
			return 0xFF64A8E8;
		if (path.contains("exhaust") || path.contains("turbo") || path.contains("muffler") || path.contains("soot"))
			return 0xFFE4684A;
		return 0xFFA79AD0;
	}

	/** A colour darkened towards the paper as it sits lower, so the layers of a stack read in the plan. */
	public static int shade(int colour, double depth) {
		double keep = 1 - 0.45 * depth;
		int r = (int) ((colour >> 16 & 0xFF) * keep);
		int g = (int) ((colour >> 8 & 0xFF) * keep);
		int b = (int) ((colour & 0xFF) * keep);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = (width - WIDTH) / 2;
		int top = (height - HEIGHT) / 2;
		graphics.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, EDGE);
		graphics.fill(left, top, left + WIDTH, top + HEIGHT, PAPER);
		for (int x = left + 10; x < left + WIDTH; x += 10)
			graphics.fill(x, top, x + 1, top + HEIGHT, PAPER_GRID);
		for (int y = top + 10; y < top + HEIGHT; y += 10)
			graphics.fill(left, y, left + WIDTH, y + 1, PAPER_GRID);

		graphics.drawString(font, title, left + 8, top + 8, INK, false);
		if (damaged) {
			graphics.drawString(font, CELang.translate("blueprint.sheet.damaged")
				.component(), left + 8, top + 28, 0xFFFF9090, false);
			return;
		}
		if (!blueprint.builder()
			.isEmpty())
			graphics.drawString(font, CELang.translate("goggles.plate.builder", blueprint.builder())
				.component(), left + 8, top + 20, INK_DIM, false);
		drawPlan(graphics, left + 8, top + 36);
		drawParts(graphics, left + 8 + DRAWING + 14, top + 36);
		drawFigures(graphics, left + 8, top + 36 + DRAWING + 8);
	}

	/** The engine from above: east is right, south is down, higher blocks drawn over lower ones. */
	private void drawPlan(GuiGraphics graphics, int x0, int y0) {
		graphics.fill(x0 - 1, y0 - 1, x0 + DRAWING + 1, y0 + DRAWING + 1, EDGE);
		graphics.fill(x0, y0, x0 + DRAWING, y0 + DRAWING, 0xFF264A80);
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		for (Blueprint.Part part : blueprint.parts()) {
			minX = Math.min(minX, part.x());
			maxX = Math.max(maxX, part.x());
			minZ = Math.min(minZ, part.z());
			maxZ = Math.max(maxZ, part.z());
			minY = Math.min(minY, part.y());
			maxY = Math.max(maxY, part.y());
		}
		int spanX = maxX - minX + 1;
		int spanZ = maxZ - minZ + 1;
		int cell = Math.max(1, Math.min(10, DRAWING / Math.max(spanX, spanZ)));
		int offsetX = x0 + (DRAWING - spanX * cell) / 2;
		int offsetY = y0 + (DRAWING - spanZ * cell) / 2;
		List<Blueprint.Part> byHeight = new ArrayList<>(blueprint.parts());
		byHeight.sort((a, b) -> Integer.compare(a.y(), b.y()));
		double range = Math.max(1, maxY - minY);
		for (Blueprint.Part part : byHeight) {
			int px = offsetX + (part.x() - minX) * cell;
			int py = offsetY + (part.z() - minZ) * cell;
			int colour = shade(colourOf(part.block()), 1 - (part.y() - minY) / range);
			graphics.fill(px, py, px + cell, py + cell, colour);
			if (cell >= 4) {
				graphics.fill(px, py, px + cell, py + 1, EDGE);
				graphics.fill(px, py, px + 1, py + cell, EDGE);
			}
		}
		graphics.drawString(font, CELang.translate("blueprint.sheet.plan")
			.component(), x0, y0 + DRAWING + 3, INK_DIM, false);
	}

	private void drawParts(GuiGraphics graphics, int x0, int y0) {
		graphics.drawString(font, CELang.translate("blueprint.sheet.parts", blueprint.parts()
			.size())
			.component(), x0, y0, INK, false);
		int y = y0 + 12;
		int shown = 0;
		int room = (HEIGHT - 36 - 12 - 48) / 10;
		for (String line : lines) {
			if (shown >= room) {
				graphics.drawString(font, CELang.translate("blueprint.sheet.more", lines.size() - shown)
					.component(), x0, y, INK_DIM, false);
				break;
			}
			graphics.drawString(font, font.plainSubstrByWidth(line, WIDTH - (x0 - (width - WIDTH) / 2) - 8), x0, y, INK_DIM,
				false);
			y += 10;
			shown++;
		}
	}

	private void drawFigures(GuiGraphics graphics, int x, int y) {
		PlateFigures figures = blueprint.figures();
		if (figures == null) {
			graphics.drawString(font, CELang.translate("blueprint.sheet.no_plate")
				.component(), x, y + 14, INK_DIM, false);
			return;
		}
		graphics.drawString(font, CELang.translate("goggles.plate.torque", MakerPlateBlockEntity.compact(figures.peakTorque()),
			figures.torqueRpm())
			.component(), x, y + 4, 0xFF9AE0FF, false);
		graphics.drawString(font, CELang.translate("goggles.plate.power", MakerPlateBlockEntity.compact(figures.peakPower()),
			figures.powerRpm(), EngineReadout.hp(EngineReadout.horsepower(figures.peakPower())))
			.component(), x, y + 16, 0xFF9AFFA8, false);
	}
}
