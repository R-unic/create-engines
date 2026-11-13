package com.createengines.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IntercoolerTest {

	private static CylinderModel cylinder(double pressure, double temperature) {
		CylinderModel cylinder = new CylinderModel(CylinderSpec.CRUDE, CamProfile.BASIC, 0, new Camshaft(), 0);
		cylinder.intakePressure = pressure;
		cylinder.intakeTemperature = temperature;
		return cylinder;
	}

	@Test
	void coolingTakesAShareOfTheCompressionHeat() {
		CylinderModel cylinder = cylinder(1.5, 1.5);
		new Intercooler(List.of(cylinder)).cool();
		assertEquals(1 + 0.5 * (1 - Intercooler.EFFECTIVENESS), cylinder.intakeTemperature, 1e-12);
	}

	@Test
	void coolingCostsAShareOfTheBoost() {
		CylinderModel cylinder = cylinder(1.5, 1.5);
		new Intercooler(List.of(cylinder)).cool();
		assertEquals(1 + 0.5 * (1 - Intercooler.PRESSURE_DROP), cylinder.intakePressure, 1e-12);
	}

	@Test
	void airAtAmbientStaysAtAmbient() {
		CylinderModel cylinder = cylinder(1, 1);
		new Intercooler(List.of(cylinder)).cool();
		assertEquals(1, cylinder.intakeTemperature, 1e-12);
		assertEquals(1, cylinder.intakePressure, 1e-12);
	}

	@Test
	void everyCylinderOnTheIntercoolerIsCooled() {
		CylinderModel a = cylinder(1.4, 1.4);
		CylinderModel b = cylinder(1.8, 1.8);
		CylinderModel apart = cylinder(1.4, 1.4);
		new Intercooler(List.of(a, b)).cool();
		assertTrue(a.intakeTemperature < 1.4);
		assertTrue(b.intakeTemperature < 1.8);
		assertEquals(1.4, apart.intakeTemperature, 1e-12);
	}

	@Test
	void severalInARowEachTakeTheirShareOfWhatIsLeft() {
		CylinderModel cylinder = cylinder(1.5, 1.5);
		Intercooler first = new Intercooler(List.of(cylinder));
		Intercooler second = new Intercooler(List.of(cylinder));
		first.cool();
		second.cool();
		double left = 1 - Intercooler.EFFECTIVENESS;
		assertEquals(1 + 0.5 * left * left, cylinder.intakeTemperature, 1e-12);
		assertTrue(cylinder.intakeTemperature > 1, "never colder than ambient");
	}

	@Test
	void aCopyCoolsOnlyTheCylindersItWasMappedTo() {
		CylinderModel original = cylinder(1.5, 1.5);
		CylinderModel unmapped = cylinder(1.5, 1.5);
		CylinderModel copy = cylinder(1.5, 1.5);

		Intercooler cooler = new Intercooler(List.of(original, unmapped));
		Intercooler copied = cooler.copyOnto(Map.of(original, copy));
		assertEquals(List.of(copy), copied.charge());
		assertEquals(2, cooler.charge().size());
	}
}
