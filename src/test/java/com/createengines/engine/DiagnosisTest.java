package com.createengines.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.createengines.engine.Diagnosis.Cause;
import com.createengines.engine.Diagnosis.Health;
import com.createengines.engine.sim.CycleResult;
import com.createengines.engine.topology.CylinderFault;

class DiagnosisTest {

	private static Map<Cause, Integer> causes(Object... pairs) {
		Map<Cause, Integer> map = new EnumMap<>(Cause.class);
		for (int i = 0; i < pairs.length; i += 2)
			map.put((Cause) pairs[i], (Integer) pairs[i + 1]);
		return map;
	}

	@Test
	void aCylinderThatFiredIsFine() {
		assertEquals(Cause.NONE, Diagnosis.ofCylinder(0, CycleResult.FIRED));
		assertEquals(Cause.NONE, Diagnosis.ofCylinder(0, CycleResult.NONE));
	}

	@Test
	void faultsAreFoundBeforeTheLastCycleIsRead() {
		assertEquals(Cause.NO_AIR, Diagnosis.ofCylinder(CylinderFault.INTAKE_BLOCKED.bit(), CycleResult.FIRED));
		assertEquals(Cause.NO_AIR, Diagnosis.ofCylinder(CylinderFault.NO_INTAKE.bit(), CycleResult.NONE));
		assertEquals(Cause.NO_EXHAUST, Diagnosis.ofCylinder(CylinderFault.EXHAUST_BLOCKED.bit(), CycleResult.NONE));
		assertEquals(Cause.NO_SPARK, Diagnosis.ofCylinder(CylinderFault.NO_BATTERY.bit(), CycleResult.NONE));
		assertEquals(Cause.NO_FUEL, Diagnosis.ofCylinder(CylinderFault.NO_INJECTOR.bit(), CycleResult.NONE));
		assertEquals(Cause.NO_CAM, Diagnosis.ofCylinder(CylinderFault.BROKEN_CAM.bit(), CycleResult.NONE));
		assertEquals(Cause.BUILD, Diagnosis.ofCylinder(CylinderFault.NO_PISTON.bit(), CycleResult.NONE));
		assertEquals(Cause.WATER, Diagnosis.ofCylinder(CylinderFault.SUBMERGED.bit(), CycleResult.NONE));
	}

	@Test
	void aMissingPartOutranksAnAirProblemOnTheSameCylinder() {
		int both = CylinderFault.NO_ROD.bit() | CylinderFault.NO_INTAKE.bit();
		assertEquals(Cause.BUILD, Diagnosis.ofCylinder(both, CycleResult.NONE));
	}

	@Test
	void theLastCycleExplainsACylinderWithNoFault() {
		assertEquals(Cause.NO_FUEL, Diagnosis.ofCylinder(0, CycleResult.NO_FUEL));
		assertEquals(Cause.NO_IGNITION, Diagnosis.ofCylinder(0, CycleResult.NO_IGNITION));
		assertEquals(Cause.TOO_RICH, Diagnosis.ofCylinder(0, CycleResult.TOO_RICH));
		assertEquals(Cause.TOO_LEAN, Diagnosis.ofCylinder(0, CycleResult.TOO_LEAN));
		assertEquals(Cause.FOULED, Diagnosis.ofCylinder(0, CycleResult.FOULED));
		assertEquals(Cause.NO_SPARK, Diagnosis.ofCylinder(0, CycleResult.NO_SPARK));
	}

	@Test
	void everyCauseHasALangKey() {
		for (Cause cause : Cause.values())
			assertEquals("goggles.cause." + cause.name().toLowerCase(java.util.Locale.ROOT), cause.langKey());
	}

	@Test
	void aSoundEngineIsHealthy() {
		assertEquals(Diagnosis.Report.HEALTHY, Diagnosis.assess(true, 4, 4, causes(), 0.01, 1, 0));
		assertEquals(Diagnosis.Report.HEALTHY, Diagnosis.assess(false, 4, 0, causes(), 0, 1, 1));
	}

	@Test
	void aRunningEngineMissingSomeCylindersIsRoughAndNamesTheCause() {
		Diagnosis.Report report = Diagnosis.assess(true, 4, 3, causes(Cause.NO_FUEL, 1), 0.1, 1, 0);
		assertEquals(Health.ROUGH, report.health());
		assertEquals(Cause.NO_FUEL, report.cause());
	}

	@Test
	void anEngineLosingHalfItsCylindersIsFailing() {
		Diagnosis.Report report = Diagnosis.assess(true, 4, 2, causes(Cause.NO_SPARK, 2), 0.2, 1, 0);
		assertEquals(Health.FAILING, report.health());
		assertEquals(Cause.NO_SPARK, report.cause());
	}

	@Test
	void aStoppedEngineWithAFaultCannotRun() {
		Diagnosis.Report report = Diagnosis.assess(false, 4, 0, causes(Cause.NO_AIR, 4), 0, 1, 1);
		assertEquals(Health.FAILING, report.health());
		assertEquals(Cause.NO_AIR, report.cause());
	}

	@Test
	void theCauseHittingMostCylindersIsTheOneNamed() {
		Diagnosis.Report report = Diagnosis.assess(true, 6, 3, causes(Cause.NO_FUEL, 1, Cause.TOO_RICH, 2), 0.1, 1, 0);
		assertEquals(Cause.TOO_RICH, report.cause());
	}

	@Test
	void aTieGoesToTheMoreFundamentalCause() {
		Diagnosis.Report report = Diagnosis.assess(true, 4, 2, causes(Cause.TOO_RICH, 1, Cause.NO_AIR, 1), 0.1, 1, 0);
		assertEquals(Cause.NO_AIR, report.cause());
	}

	@Test
	void dryBearingsRoughenAndSeizingFails() {
		assertEquals(new Diagnosis.Report(Health.ROUGH, Cause.DRY_OIL), Diagnosis.assess(true, 4, 4, causes(), 0, 0.3, 0));
		assertEquals(new Diagnosis.Report(Health.FAILING, Cause.DRY_OIL), Diagnosis.assess(true, 4, 4, causes(), 0, 0.05, 0));
	}

	@Test
	void unevenFiringAndAColdBlockAreRoughWithNoCylinderAtFault() {
		assertEquals(new Diagnosis.Report(Health.ROUGH, Cause.UNEVEN), Diagnosis.assess(true, 2, 2, causes(), 0.2, 1, 0));
		assertEquals(new Diagnosis.Report(Health.ROUGH, Cause.COLD), Diagnosis.assess(true, 4, 4, causes(), 0, 1, 0.9));
	}

	@Test
	void anEngineWithNoWorkingCylindersIsFailing() {
		assertEquals(new Diagnosis.Report(Health.FAILING, Cause.BUILD), Diagnosis.assess(false, 0, 0, causes(), 0, 1, 0));
	}
}
