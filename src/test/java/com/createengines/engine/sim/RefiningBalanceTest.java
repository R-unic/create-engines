package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * What a bucket of each fuel costs by each route, read from the recipe files that ship. The column is the better way to
 * make a fuel and the basin is the worse one, and the basin's diesel and gasoline are not changed by 1.3, so a
 * factory built before it keeps making what it made. Ethanol is the one fuel that gets dearer: the column's figure
 * sits between today's basin and the weakened basin 1.3 ships.
 * <p>
 * Costs here are per bucket (1000 mB) of product: mB of feed, and catalyst items. {@code docs/REFINING_BALANCE.md}
 * has the table these numbers make.
 */
class RefiningBalanceTest {

	private static final Path DATA = Path.of("src/main/resources/data/create_engines/recipe");

	/** The basin's ethanol after 1.3: 250 mB of mash gives this much, heated and superheated. */
	static final int WEAKENED_BASIN_HEATED = 35;
	static final int WEAKENED_BASIN_SUPERHEATED = 70;

	/** What a bucket costs from one route: feed in mB, and catalyst items. */
	private record Cost(double feedMb, double items) {
	}

	private static JsonObject read(String file) throws IOException {
		try (Reader in = Files.newBufferedReader(DATA.resolve(file))) {
			return JsonParser.parseReader(in).getAsJsonObject();
		}
	}

	static RefiningRoute column(String file) throws IOException {
		JsonObject json = read("refining/" + file + ".json");
		int feed = json.getAsJsonObject("feed").get("amount").getAsInt();
		int product = json.getAsJsonObject("product").get("amount").getAsInt();
		Heat heat = json.has("heat") ? Heat.fromName(json.get("heat").getAsString()) : Heat.HEATED;
		double speed = json.has("speed") ? json.get("speed").getAsDouble() : 1;
		int catalyst = json.has("catalyst") ? json.getAsJsonObject("catalyst").get("feed_per_item").getAsInt() : 0;
		return new RefiningRoute(feed, product, heat, catalyst, speed);
	}

	/** A basin recipe's cost per bucket: the fluid in, the item ingredients, the fluid out. */
	private static Cost basin(String file, String product) throws IOException {
		JsonObject json = read("mixing/" + file + ".json");
		double fluidIn = 0, items = 0;
		for (var element : json.getAsJsonArray("ingredients")) {
			JsonObject ingredient = element.getAsJsonObject();
			if (ingredient.has("fluid")) {
				if (fluidIn == 0)
					fluidIn = ingredient.get("amount").getAsDouble();
			} else
				items++;
		}
		double out = 0;
		for (var element : json.getAsJsonArray("results")) {
			JsonObject result = element.getAsJsonObject();
			if (result.get("id").getAsString().endsWith(":" + product))
				out = result.get("amount").getAsDouble();
		}
		double scale = 1000 / out;
		return new Cost(fluidIn * scale, items * scale);
	}

	private static Cost of(RefiningRoute route) {
		return new Cost(route.feedPerBucket(), route.catalystPerBucket());
	}

	@Test
	void theShippedRoutesParse() throws IOException {
		assertEquals(Heat.HEATED, column("diesel").heat());
		assertEquals(Heat.HEATED, column("gasoline").heat());
		assertEquals(Heat.HEATED, column("ethanol").heat());
		assertEquals(Heat.SUPERHEATED, column("ethanol_superheated").heat());
	}

	@Test
	void theColumnMakesDieselFromLessFeedAndMuchLessCatalystThanTheBasin() throws IOException {
		Cost basin = basin("diesel", "diesel");
		Cost column = of(column("diesel"));
		assertEquals(1000, basin.feedMb(), 1e-9);
		assertEquals(4, basin.items(), 1e-9);
		assertTrue(column.feedMb() < basin.feedMb() * 0.95, "feed " + column.feedMb());
		assertTrue(column.items() < basin.items() / 3, "catalyst " + column.items());
	}

	@Test
	void theColumnMakesGasolineFromLessFeedAndMuchLessCatalystThanTheBasin() throws IOException {
		Cost basin = basin("gasoline", "gasoline");
		Cost column = of(column("gasoline"));
		assertEquals(1000, basin.feedMb(), 1e-9);
		assertEquals(4, basin.items(), 1e-9);
		assertTrue(column.feedMb() < basin.feedMb() * 0.95, "feed " + column.feedMb());
		assertTrue(column.items() < basin.items() / 3, "catalyst " + column.items());
	}

	@Test
	void aBucketOfGasolineFromSeedOilTakesLessOilByColumnThanByBasin() throws IOException {
		double byBasin = basin("diesel", "diesel").feedMb() * basin("gasoline", "gasoline").feedMb() / 1000;
		double byColumn = of(column("diesel")).feedMb() * of(column("gasoline")).feedMb() / 1000;
		assertTrue(byColumn < 0.85 * byBasin, "oil per bucket of gasoline: " + byColumn + " against " + byBasin);
	}

	@Test
	void ethanolIsTheOneFuelTheColumnMakesDearerThanTodaysBasin() throws IOException {
		double todayHeated = basin("ethanol", "ethanol").feedMb();
		double todaySuper = basin("ethanol_superheated", "ethanol").feedMb();
		assertEquals(5000, todayHeated, 1e-9);
		assertEquals(2500, todaySuper, 1e-9);
		assertTrue(of(column("ethanol")).feedMb() > todayHeated, "heated column feed " + of(column("ethanol")).feedMb());
		assertTrue(of(column("ethanol_superheated")).feedMb() > todaySuper,
			"superheated column feed " + of(column("ethanol_superheated")).feedMb());
	}

	@Test
	void ethanolByColumnIsBetterThanTheWeakenedBasin() {
		double weakHeated = 250.0 / WEAKENED_BASIN_HEATED * 1000;
		double weakSuper = 250.0 / WEAKENED_BASIN_SUPERHEATED * 1000;
		try {
			assertTrue(of(column("ethanol")).feedMb() < weakHeated);
			assertTrue(of(column("ethanol_superheated")).feedMb() < weakSuper);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void superheatedEthanolGivesTwiceTheHeatedYield() throws IOException {
		assertEquals(2.0, column("ethanol_superheated").yield() / column("ethanol").yield(), 1e-9);
	}

	@Test
	void theDearFuelsComeOverSlower() throws IOException {
		assertTrue(column("diesel").speed() >= column("gasoline").speed());
		assertTrue(column("gasoline").speed() > column("ethanol").speed());
		assertEquals(column("ethanol").speed(), column("ethanol_superheated").speed(), 0);
	}

	@Test
	void ethanolTakesNetherWartAndAnEthanolBucketCostsAHandfulOfIt() throws IOException {
		double heated = of(column("ethanol")).items();
		double superheated = of(column("ethanol_superheated")).items();
		assertEquals(heated / 2, superheated, 1e-9);
		assertTrue(heated > 3 && heated < 10, "wart per heated bucket: " + heated);
	}
}
