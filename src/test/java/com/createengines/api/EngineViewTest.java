package com.createengines.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The read-only view a script or another mod gets of an engine: it holds exactly what it was given and compares by value. */
class EngineViewTest {

	private static EngineView view(float rpm, String health) {
		return new EngineView(EngineStatus.RUNNING, rpm, 3.5f, 1.25f, 4, 4, 0.7f, 0.9f, 1.5f, health, "none");
	}

	@Test
	void everyFieldComesBackAsGiven() {
		EngineView view = view(1200f, "healthy");
		assertEquals(EngineStatus.RUNNING, view.status());
		assertEquals(1200f, view.rpm());
		assertEquals(3.5f, view.availableTorque());
		assertEquals(1.25f, view.loadTorque());
		assertEquals(4, view.cylinders());
		assertEquals(4, view.firing());
		assertEquals(0.7f, view.temperature());
		assertEquals(0.9f, view.oilCondition());
		assertEquals(1.5f, view.boost());
		assertEquals("healthy", view.health());
		assertEquals("none", view.cause());
	}

	@Test
	void twoViewsOfTheSameStateAreEqualAndOneOfAnotherIsNot() {
		assertEquals(view(1200f, "healthy"), view(1200f, "healthy"));
		assertEquals(view(1200f, "healthy").hashCode(), view(1200f, "healthy").hashCode());
		assertNotEquals(view(1200f, "healthy"), view(1300f, "healthy"));
		assertNotEquals(view(1200f, "healthy"), view(1200f, "rough"));
	}

	@Test
	void itPrintsItsStatusAndSpeed() {
		String text = view(1200f, "healthy").toString();
		assertTrue(text.contains("RUNNING") && text.contains("1200"), text);
	}
}
