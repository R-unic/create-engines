package com.createengines.content.display;

import com.createengines.content.transmission.Gears;
import com.createengines.content.transmission.TransmissionBlockEntity;
import com.createengines.registry.CELang;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.network.chat.MutableComponent;

/**
 * A Display Link set against a transmission: the gear it is in now, as the goggles name it ("Gear 7", "Neutral",
 * "Reverse"), with "(auto)" after it while the block is choosing the gear itself. On auto it shows the gear in use, not
 * the letter, so a board shows what the engine is actually pulling in.
 */
public class TransmissionGearDisplaySource extends SingleLineDisplaySource {

	@Override
	protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
		if (!(context.getSourceBlockEntity() instanceof TransmissionBlockEntity box))
			return CELang.translate("display.transmission.none")
				.component();
		return line(box.gear(), box.isAuto());
	}

	/** The line for a gear: its name, and "(auto)" after a numbered gear the block chose for itself. */
	public static MutableComponent line(int gear, boolean auto) {
		MutableComponent line = CELang.translate(Gears.displayKey(gear), Gears.label(gear))
			.component();
		return auto && Gears.isForward(gear) ? line.append(CELang.translate("display.transmission.auto")
			.component()) : line;
	}

	@Override
	protected boolean allowsLabeling(DisplayLinkContext context) {
		return true;
	}

	@Override
	public int getPassiveRefreshTicks() {
		return EngineReadout.REFRESH_TICKS;
	}
}
