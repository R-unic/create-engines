package com.createengines.gametest;

import com.createengines.CreateEngines;
import com.createengines.api.EngineStatus;
import com.createengines.api.EngineView;
import com.createengines.compat.jade.EngineTooltip;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The words the Jade overlay shows ({@link EngineTooltip}), which need no Jade: the server's engine view survives the trip through
 * a tag, and whatever a tag holds, the client turns it into lines without throwing. Jade finding the plugin is a compatibility row
 * ({@code runSmokeAuditJade}), not a test here.
 */
@GameTestHolder(CreateEngines.MOD_ID)
@PrefixGameTestTemplate(false)
public class JadeTooltipGameTests {

	private static String text(java.util.List<Component> lines) {
		StringBuilder all = new StringBuilder();
		for (Component line : lines)
			all.append(line.getString())
				.append('\n');
		return all.toString();
	}

	private static EngineView view(EngineStatus status, float rpm, float boost, String health) {
		return new EngineView(status, rpm, 10, 5, 4, 2, 0.5f, 0.9f, boost, health, "none");
	}

	private static CompoundTag data(EngineView view) {
		CompoundTag data = new CompoundTag();
		data.put(EngineTooltip.KEY, EngineTooltip.write(view));
		return data;
	}

	@GameTest(template = "empty")
	public static void aRunningEngineReadsAsRunningWithItsSpeed(GameTestHelper helper) {
		String text = text(EngineTooltip.lines(data(view(EngineStatus.RUNNING, 118.4f, 0, "healthy"))));
		helper.assertTrue(text.contains("Running"), "The state should be named: " + text);
		helper.assertTrue(text.contains("118 RPM"), "The speed should be rounded and shown: " + text);
		helper.assertTrue(!text.contains("health") && !text.toLowerCase()
			.contains("boost"), "A healthy engine with no boost says nothing about either: " + text);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void aFailingBoostedEngineSaysSo(GameTestHelper helper) {
		String text = text(EngineTooltip.lines(data(view(EngineStatus.RUNNING, 90, 1.5f, "failing"))));
		helper.assertTrue(text.contains("Failing"), "A failing engine says so: " + text);
		helper.assertTrue(text.contains("Boost: 1.5"), "The boost is shown with one decimal: " + text);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void everyStateHasAName(GameTestHelper helper) {
		for (EngineStatus status : EngineStatus.values()) {
			String text = text(EngineTooltip.lines(data(view(status, 0, 0, "healthy"))));
			helper.assertTrue(!text.isBlank() && !text.contains("create_engines."), status + " has no readable line: " + text);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void nothingWrongInATagMakesTheTooltipThrow(GameTestHelper helper) {
		helper.assertTrue(EngineTooltip.lines(null)
			.isEmpty(), "No data means no lines");
		helper.assertTrue(EngineTooltip.lines(new CompoundTag())
			.isEmpty(), "A tag with no engine means no lines");

		CompoundTag unknown = new CompoundTag();
		CompoundTag engine = new CompoundTag();
		engine.putString("Status", "a-state-from-the-future");
		unknown.put(EngineTooltip.KEY, engine);
		helper.assertTrue(EngineTooltip.lines(unknown)
			.isEmpty(), "A state this version does not know is shown as nothing, not a crash");

		CompoundTag wrongType = new CompoundTag();
		wrongType.putInt(EngineTooltip.KEY, 7);
		helper.assertTrue(EngineTooltip.lines(wrongType)
			.isEmpty(), "An engine that is not a compound reads as none");

		CompoundTag nonsense = data(view(EngineStatus.RUNNING, Float.NaN, Float.POSITIVE_INFINITY, "healthy"));
		String text = text(EngineTooltip.lines(nonsense));
		helper.assertTrue(text.contains("Running") && !text.contains("NaN") && !text.contains("Infinity"),
			"Speeds that are not numbers are left out: " + text);
		helper.succeed();
	}
}
