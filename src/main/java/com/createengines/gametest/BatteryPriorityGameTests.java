package com.createengines.gametest;

import java.util.List;

import com.createengines.CreateEngines;
import com.createengines.content.battery.BatteryBank;
import com.createengines.content.battery.BatteryBlockEntity;
import com.createengines.engine.BatteryIgnition;
import com.createengines.engine.sim.BatteryEnergy;
import com.createengines.engine.sim.ChargePriority;
import com.createengines.registry.CEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Charge priority: a battery the wrench sets to reserve is kept for the starter and the spark plugs. Another mod's wire (Forge
 * Energy) cannot draw on it, a spark can, and an alternator fills it first. A bank with no reserve is shared as it always was.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class BatteryPriorityGameTests {

	private static final String BATCH = "battery-priority";

	private static final BlockPos A = new BlockPos(6, 1, 1);
	private static final BlockPos B = new BlockPos(6, 1, 2);
	private static final BlockPos C = new BlockPos(6, 1, 3);

	private static BatteryBlockEntity place(GameTestHelper helper, BlockPos pos, float charge) {
		helper.setBlock(pos, CEBlocks.BATTERY.getDefaultState());
		BatteryBlockEntity battery = helper.getBlockEntity(pos);
		battery.charge(charge);
		return battery;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1e-3;
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBatteryIsNotAReserveUntilSomeoneSetsIt(GameTestHelper helper) {
		BatteryBlockEntity battery = place(helper, A, 100);
		helper.assertTrue(!battery.isReserve(), "A new battery gives to everything");
		battery.setReserve(true);
		helper.assertTrue(battery.isReserve(), "The wrench sets it");
		battery.setReserve(false);
		helper.assertTrue(!battery.isReserve(), "and clears it");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anotherModsWireCannotDrawOnAReserve(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 100);
		BatteryBlockEntity b = place(helper, B, 100);
		BatteryBlockEntity c = place(helper, C, 100);
		b.setReserve(true);
		IEnergyStorage wire = a.energyOutput();
		int offered = wire.getEnergyStored();
		helper.assertTrue(offered == BatteryEnergy.toEnergy(200), "The wire sees only the two open batteries' 200 sparks: " + offered);
		int out = wire.extractEnergy(Integer.MAX_VALUE, false);
		helper.assertTrue(out == BatteryEnergy.toEnergy(200), "and can take only that: " + out);
		helper.assertTrue(near(a.charge(), 0) && near(c.charge(), 0), "the open batteries ran down: " + a.charge() + ", " + c.charge());
		helper.assertTrue(near(b.charge(), 100), "the reserve is untouched: " + b.charge());
		helper.assertTrue(wire.extractEnergy(1000, false) == 0, "and once the open ones are flat the wire gets nothing");
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aSparkOrAStarterCanStillDrawOnTheReserveLast(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 100);
		BatteryBlockEntity b = place(helper, B, 100);
		BatteryBlockEntity c = place(helper, C, 100);
		b.setReserve(true);
		List<BatteryBlockEntity> bank = a.bank();
		helper.assertTrue(bank.size() == 3, "The three are one bank: " + bank.size());
		float taken = BatteryBank.drawUpTo(bank, 100);
		helper.assertTrue(near(taken, 100) && near(a.charge(), 50) && near(c.charge(), 50) && near(b.charge(), 100),
			"A small essential draw is shared by the open batteries: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		taken = BatteryBank.drawUpTo(bank, 150);
		helper.assertTrue(near(taken, 150) && near(a.charge(), 0) && near(c.charge(), 0) && near(b.charge(), 50),
			"A larger one empties them and then takes the rest from the reserve: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anEnginesPlugsFireFromABankThatIsAllReserve(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 5);
		a.setReserve(true);
		BatteryIgnition plugs = new BatteryIgnition(helper.getLevel(), List.of(helper.absolutePos(A)));
		helper.assertTrue(plugs.powered(), "A reserve powers the plugs");
		helper.assertTrue(plugs.spark(), "and a spark is drawn from it");
		helper.assertTrue(a.charge() < 5, "using up charge: " + a.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void aBankWithNoReserveIsSharedAsItAlwaysWas(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 100);
		BatteryBlockEntity b = place(helper, B, 50);
		BatteryBlockEntity c = place(helper, C, 50);
		List<BatteryBlockEntity> bank = a.bank();
		BatteryBank.drawUpTo(bank, 100);
		helper.assertTrue(near(a.charge(), 50) && near(b.charge(), 25) && near(c.charge(), 25),
			"An essential draw is in proportion to what each holds: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		float taken = BatteryBank.drawUpTo(bank, 20, ChargePriority.Use.OPTIONAL);
		helper.assertTrue(near(taken, 20) && near(a.charge(), 40) && near(b.charge(), 20) && near(c.charge(), 20),
			"and an optional one is the same with no reserve: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		BatteryBank.fill(bank, 90);
		helper.assertTrue(near(a.charge() + b.charge() + c.charge(), 170), "Filling adds what was put in: " + (a.charge() + b.charge() + c.charge()));
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void anAlternatorFillsAReserveFirst(GameTestHelper helper) {
		BatteryBlockEntity a = place(helper, A, 0);
		BatteryBlockEntity b = place(helper, B, 0);
		BatteryBlockEntity c = place(helper, C, 0);
		b.setReserve(true);
		float put = BatteryBank.fill(a.bank(), 150);
		helper.assertTrue(near(put, 150) && near(b.charge(), 150) && near(a.charge(), 0) && near(c.charge(), 0),
			"A reserve fills first: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		BatteryBank.fill(a.bank(), 150);
		helper.assertTrue(near(b.charge(), 200) && near(a.charge(), 50) && near(c.charge(), 50),
			"and when it is full the charge goes to the rest: " + a.charge() + ", " + b.charge() + ", " + c.charge());
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void theReserveIsSavedAndAnOldSaveIsNone(GameTestHelper helper) {
		BatteryBlockEntity battery = place(helper, A, 100);
		CompoundTag plain = battery.saveWithoutMetadata(helper.getLevel()
			.registryAccess());
		helper.assertTrue(!plain.contains("Reserve"), "A battery nobody set saves exactly what it always did: " + plain.getAllKeys());
		battery.setReserve(true);
		CompoundTag saved = battery.saveWithoutMetadata(helper.getLevel()
			.registryAccess());
		helper.assertTrue(saved.getBoolean("Reserve"), "A reserve is saved");
		BatteryBlockEntity other = place(helper, A.east(2), 0);
		other.loadWithComponents(saved, helper.getLevel()
			.registryAccess());
		helper.assertTrue(other.isReserve(), "and loads as one");
		other.loadWithComponents(plain, helper.getLevel()
			.registryAccess());
		helper.assertTrue(!other.isReserve(), "A save from before reserves is no reserve");
		helper.succeed();
	}
}
