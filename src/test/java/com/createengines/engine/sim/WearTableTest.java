package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Writes the wear mode's numbers as tables in docs/WEAR.md, worked out from the real rules, so the author can tune them with the figures in front
 * of them and the document can never say something the code does not. The test compares what the code gives with the committed file; with
 * {@code WEAR_TABLE_REGEN=1} in the environment it writes the file instead.
 */
class WearTableTest {

	private static final Path FILE = Path.of("docs/WEAR.md");
	private static final double[] RPMS = {32, 64, 128, 192, 256};

	private static String f(double value, int decimals) {
		return String.format(Locale.ROOT, "%." + decimals + "f", value);
	}

	private static String hours(double perSecond) {
		return perSecond <= 0 ? "never" : f(1 / perSecond / 3600, 1);
	}

	private static double peak(Wear.State wear) {
		Camshaft cam = new Camshaft();
		List<CylinderModel> models = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double fire = i * 180;
			CylinderModel model = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, fire % 360, cam, fire / 2);
			model.fuelSupply = MixtureTest.unlimited(FuelSpec.GASOLINE);
			model.ignition = IgnitionSource.UNLIMITED;
			model.wear = wear;
			models.add(model);
		}
		EngineSimulation sim = new EngineSimulation(models, models.size() * 0.35 + 1, (models.size() * 2 + 1) * 0.01);
		for (CylinderModel model : models)
			model.resetGas(0);
		double best = 0;
		for (double t : Dynamometer.torqueCurve(sim))
			best = Math.max(best, t);
		return best;
	}

	static String table() {
		StringBuilder out = new StringBuilder();
		double warm = Thermal.NORMAL;
		double hot = Thermal.NORMAL + 0.2;
		out.append("# Wear and tear: the numbers\n\n");
		out.append("These are the wear mode's figures as the code gives them today, every one a first pass for the author to tune. This file is written by\n");
		out.append("`WearTableTest` from the real rules, and the test fails if it no longer matches them: change a number in `Wear.java`, run the\n");
		out.append("test with `WEAR_TABLE_REGEN=1`, and the diff of this file is the review.\n\n");

		out.append("## How long a part lasts\n\n");
		out.append("Hours of running from new to spent (wear 1), one engine on good oil, no boost. Wear grows with the square of speed up to four times the\n");
		out.append("reference speed of " + f(Wear.REFERENCE_RPM, 0) + " RPM, with a floor of ");
		out.append(f(Wear.IDLE_FLOOR * 100, 0) + "% when it is barely turning. Heat adds 2.5 per unit over normal, no oil up to five times.\n\n");
		out.append("| Part | " + join(RPMS, " RPM | ") + " RPM |\n| --- |" + " ---: |".repeat(RPMS.length) + "\n");
		out.append(row("Piston, warm", rpm -> Wear.pistonWear(rpm, warm, 1, 1)));
		out.append(row("Piston, hot", rpm -> Wear.pistonWear(rpm, hot, 1, 1)));
		out.append(row("Piston, no oil", rpm -> Wear.pistonWear(rpm, warm, 0, 1)));
		out.append(row("Forged (precision) piston, warm", rpm -> Wear.pistonWear(rpm, warm, 1, 1, true)));
		out.append(row("Connecting rod, warm", rpm -> Wear.rodWear(rpm, 1, 1)));
		out.append(row("Connecting rod, no oil", rpm -> Wear.rodWear(rpm, 0, 1)));
		for (Wear.Gasket tier : Wear.Gasket.values())
			out.append(row("Gasket, " + tier.name().toLowerCase(Locale.ROOT) + ", warm, no boost", rpm -> Wear.gasketWear(tier, rpm, warm, 1, 1)));
		for (Wear.Gasket tier : Wear.Gasket.values())
			out.append(row("Gasket, " + tier.name().toLowerCase(Locale.ROOT) + ", warm, 1 bar of boost", rpm -> Wear.gasketWear(tier, rpm, warm, 2, 1)));
		out.append("\n");

		out.append("## What a gasket is worth\n\n");
		out.append("A fitted gasket keeps more of each burn's heat than a head with none fitted, with the wear mode on or off. With it on the bonus fades as the\n");
		out.append("gasket wears, and a worn gasket also loses power. A head with none fitted is the baseline (100%).\n\n");
		out.append("| Gasket | Bonus when new | Peak torque, new | Peak torque, half worn | Peak torque, spent |\n| --- | ---: | ---: | ---: | ---: |\n");
		double base = peak(Wear.NONE);
		out.append("| none fitted | 0% | 100.0% | - | - |\n");
		for (Wear.Gasket tier : Wear.Gasket.values())
			out.append("| " + tier.name().toLowerCase(Locale.ROOT) + " | " + f(tier.bonus * 100, 1) + "% | "
				+ f(peak(Wear.of(0, tier, 0, true)) / base * 100, 1) + "% | "
				+ f(peak(Wear.of(0, tier, 0.5, true)) / base * 100, 1) + "% | "
				+ f(peak(Wear.of(0, tier, 1, true)) / base * 100, 1) + "% |\n");
		out.append("\n");

		out.append("## What wear costs\n\n");
		out.append("Peak torque of a four-cylinder gasoline engine on the dyno, as a share of a sound one, with a piston and a stock gasket worn the same. Failure\n");
		out.append("is not possible below " + f(Wear.FAILURE_FROM, 2) + " wear.\n\n");
		out.append("| Wear | Peak torque | Drag on the crank |\n| ---: | ---: | ---: |\n");
		for (double wear : new double[] {0, 0.25, 0.5, 0.75, 1})
			out.append("| " + f(wear, 2) + " | " + f(peak(Wear.of(wear, Wear.Gasket.STOCK, wear)) / base * 100, 1) + "% | "
				+ f(Wear.pistonDrag(wear), 2) + "x |\n");
		out.append("\n");

		out.append("## How likely a failure is\n\n");
		out.append("The chance in one minute that a part fails (a rod is thrown, a gasket blows), by how worn it is and how fast the engine turns.\n\n");
		out.append("| Wear | " + join(RPMS, " RPM | ") + " RPM |\n| ---: |" + " ---: |".repeat(RPMS.length) + "\n");
		for (double wear : new double[] {0.5, 0.6, 0.7, 0.8, 0.9, 1})
			out.append(failureRow(wear));
		out.append("\n");

		out.append("## Knock\n\n");
		out.append("A spark engine whose boost lights the charge early wears its pistons on top of the ordinary wear above, from " + f(Knock.FROM * 100, 0)
			+ "% of the burn lost, and is heard\nfrom " + f(Knock.AUDIBLE_FROM * 100, 0)
			+ "%. Hours for knock alone to take a standard piston from new to spent; a forged piston lasts " + f(Wear.PRECISION_LIFE, 1)
			+ " times as long. It breaks nothing by itself.\n\n");
		out.append("| Burn lost | " + join(RPMS, " RPM | ") + " RPM |\n| ---: |" + " ---: |".repeat(RPMS.length) + "\n");
		for (double loss : new double[] {0.07, 0.15, 0.25, FuelSpec.PREIGNITION_MAX_LOSS})
			out.append(row(f(loss * 100, 0) + "%", rpm -> Knock.pistonWear(loss, rpm, 1)));
		out.append("\n");

		out.append("## Repair\n\n");
		out.append("A Service Kit has " + 24 + " uses. It sets wear above " + f(Wear.REPAIRED_TO, 2) + " and up to " + f(Wear.REPAIRABLE, 2)
			+ " back to " + f(Wear.REPAIRED_TO, 2) + "; heavier wear needs a new part.\n\n");

		out.append("## Running dry\n\n");
		out.append("A running engine whose oil film is below " + f(Wear.SEIZE_FILM, 2) + " counts a cylinder towards seizing, and oil takes the count down "
			+ f(Wear.SEIZE_RECOVERY, 0) + " seconds for each second. A seized piston is left at least " + f(Wear.SEIZED_WEAR, 2) + " worn and its cylinder drags "
			+ f(Wear.SEIZED_DRAG, 0) + " times as hard. It cries out from half way, more often the nearer it is. A forged (precision) piston lasts "
			+ f(Wear.PRECISION_LIFE, 1) + " times as long.\n\n");
		out.append("Seconds of running dry before a piston seizes:\n\n");
		out.append("| Piston | 32 RPM | 64 RPM | 100 RPM | 128 RPM | 192 RPM | 256 RPM |\n");
		out.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n");
		for (boolean forged : new boolean[] {false, true}) {
			out.append(forged ? "| Forged (precision) |" : "| Standard |");
			for (double rpm : new double[] {32, 64, 100, 128, 192, 256})
				out.append(" " + f(Wear.SEIZE_AFTER / Wear.dryTime(0, 0, rpm, 1, forged), 0) + " |");
			out.append('\n');
		}
		return out.toString();
	}

	private interface Rate {
		double perSecond(double rpm);
	}

	private static String join(double[] values, String between) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < values.length; i++)
			out.append(i == 0 ? "" : between).append(f(values[i], 0));
		return out.toString();
	}

	private static String row(String name, Rate rate) {
		StringBuilder out = new StringBuilder("| " + name + " |");
		for (double rpm : RPMS)
			out.append(" ").append(hours(rate.perSecond(rpm))).append(" |");
		return out.append("\n").toString();
	}

	private static String failureRow(double wear) {
		StringBuilder out = new StringBuilder("| " + f(wear, 2) + " |");
		for (double rpm : RPMS)
			out.append(" ").append(f(Wear.failureChance(wear, rpm, 60) * 100, 1)).append("% |");
		return out.append("\n").toString();
	}

	@Test
	void theWearDocumentSaysWhatTheCodeDoes() throws IOException {
		String current = table();
		if ("1".equals(System.getenv("WEAR_TABLE_REGEN"))) {
			Files.writeString(FILE, current, StandardCharsets.UTF_8);
			return;
		}
		String committed = Files.exists(FILE) ? Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
		assertEquals(current, committed, "docs/WEAR.md is out of date: run the test with WEAR_TABLE_REGEN=1 and review the diff");
	}
}
