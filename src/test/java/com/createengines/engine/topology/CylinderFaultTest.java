package com.createengines.engine.topology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CylinderFaultTest {

	private static int of(CylinderFault... faults) {
		int mask = 0;
		for (CylinderFault fault : faults)
			mask |= fault.bit();
		return mask;
	}

	@Test
	void everyFaultHasItsOwnBitInAnInt() {
		assertTrue(CylinderFault.values().length <= 31);
		Set<Integer> bits = new HashSet<>();
		for (CylinderFault fault : CylinderFault.values()) {
			assertEquals(Integer.bitCount(fault.bit()), 1, fault.name());
			assertTrue(bits.add(fault.bit()), fault.name());
		}
	}

	@Test
	void aFaultIsInAMaskOnlyIfItsBitIsSet() {
		int mask = of(CylinderFault.NO_CAM, CylinderFault.NO_BATTERY);
		assertTrue(CylinderFault.NO_CAM.in(mask));
		assertTrue(CylinderFault.NO_BATTERY.in(mask));
		assertFalse(CylinderFault.NO_PISTON.in(mask));
		for (CylinderFault fault : CylinderFault.values())
			assertFalse(fault.in(0));
	}

	@Test
	void langKeysAreLowerCaseAndDistinct() {
		assertEquals("goggles.fault.not_on_throw", CylinderFault.NOT_ON_THROW.langKey());
		Set<String> keys = new HashSet<>();
		for (CylinderFault fault : CylinderFault.values())
			assertTrue(keys.add(fault.langKey()), fault.name());
	}

	@Test
	void aCylinderWithNoFaultsIsNotDisabled() {
		assertFalse(CylinderFault.disables(0));
	}

	@Test
	void onlyTheFaultsThatMakeItNotAPistonDisableTheCylinder() {
		for (CylinderFault fault : CylinderFault.values())
			assertEquals(fault.disablesCylinder, CylinderFault.disables(fault.bit()), fault.name());
		assertTrue(CylinderFault.disables(of(CylinderFault.NO_INJECTOR, CylinderFault.NO_ROD)));
		assertFalse(CylinderFault.disables(of(CylinderFault.NO_INJECTOR, CylinderFault.NO_CAM)));
	}

	@Test
	void theStructuralFaultsDisableAndTheAirAndFuelOnesDoNot() {
		for (CylinderFault fault : new CylinderFault[] {CylinderFault.NOT_ON_THROW, CylinderFault.NO_PISTON,
			CylinderFault.NO_ROD, CylinderFault.NO_HEAD, CylinderFault.NO_ROTOR, CylinderFault.ROTARY_DISABLED})
			assertTrue(fault.disablesCylinder, fault.name());
		for (CylinderFault fault : new CylinderFault[] {CylinderFault.NO_INTAKE, CylinderFault.INTAKE_BLOCKED,
			CylinderFault.NO_EXHAUST, CylinderFault.SUBMERGED, CylinderFault.NO_BATTERY, CylinderFault.NO_PLUG})
			assertFalse(fault.disablesCylinder, fault.name());
	}

	@Test
	void theIntakeIsShutByAnythingThatStopsAirReachingTheCylinder() {
		assertFalse(CylinderFault.intakeShut(0));
		for (CylinderFault fault : new CylinderFault[] {CylinderFault.NO_INTAKE, CylinderFault.INTAKE_BLOCKED,
			CylinderFault.INTAKE_FLOODED, CylinderFault.SUBMERGED})
			assertTrue(CylinderFault.intakeShut(fault.bit()), fault.name());
		assertFalse(CylinderFault.intakeShut(of(CylinderFault.NO_EXHAUST, CylinderFault.EXHAUST_BLOCKED)));
	}

	@Test
	void theExhaustIsShutByAnythingThatStopsGasLeavingTheCylinder() {
		assertFalse(CylinderFault.exhaustShut(0));
		for (CylinderFault fault : new CylinderFault[] {CylinderFault.NO_EXHAUST, CylinderFault.EXHAUST_BLOCKED,
			CylinderFault.EXHAUST_FLOODED, CylinderFault.SUBMERGED})
			assertTrue(CylinderFault.exhaustShut(fault.bit()), fault.name());
		assertFalse(CylinderFault.exhaustShut(of(CylinderFault.NO_INTAKE, CylinderFault.INTAKE_BLOCKED)));
	}

	@Test
	void aSubmergedCylinderShutsBothEnds() {
		int mask = of(CylinderFault.SUBMERGED);
		assertTrue(CylinderFault.intakeShut(mask));
		assertTrue(CylinderFault.exhaustShut(mask));
		assertTrue(CylinderFault.underwater(mask));
	}

	@Test
	void onlyFluidFaultsCountAsUnderwater() {
		assertFalse(CylinderFault.underwater(0));
		assertTrue(CylinderFault.underwater(of(CylinderFault.INTAKE_FLOODED)));
		assertTrue(CylinderFault.underwater(of(CylinderFault.EXHAUST_FLOODED)));
		assertFalse(CylinderFault.underwater(of(CylinderFault.INTAKE_BLOCKED, CylinderFault.NO_EXHAUST)));
	}

	@Test
	void aWorkingCylinderLoadsTheCrankFully() {
		assertEquals(1, CylinderFault.dragOnCrank(0), 0);
		assertEquals(1, CylinderFault.dragOnCrank(of(CylinderFault.NO_HEAD)), 0, "a rod and piston still drag");
		assertEquals(1, CylinderFault.dragOnCrank(of(CylinderFault.NO_INJECTOR, CylinderFault.NO_CAM)), 0);
	}

	@Test
	void aMissingPistonHalvesTheDrag() {
		assertEquals(0.5, CylinderFault.dragOnCrank(of(CylinderFault.NO_PISTON)), 0);
		assertEquals(0.5, CylinderFault.dragOnCrank(of(CylinderFault.NO_PISTON, CylinderFault.NO_HEAD)), 0);
	}

	@Test
	void nothingConnectedToTheCrankMeansNoDrag() {
		for (CylinderFault fault : new CylinderFault[] {CylinderFault.NOT_ON_THROW, CylinderFault.NO_ROD,
			CylinderFault.NO_ROTOR, CylinderFault.ROTARY_DISABLED})
			assertEquals(0, CylinderFault.dragOnCrank(fault.bit()), 0, fault.name());
		assertEquals(0, CylinderFault.dragOnCrank(of(CylinderFault.NO_PISTON, CylinderFault.NO_ROD)), 0);
	}
}
