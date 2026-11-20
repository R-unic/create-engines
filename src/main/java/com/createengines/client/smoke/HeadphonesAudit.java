package com.createengines.client.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.createengines.CreateEngines;
import com.createengines.client.HeadphoneMuffle;
import com.createengines.registry.CEItems;
import com.createengines.smoke.SmokeReport;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The noise-cancelling headphones, on the real sound card: with a fleet of engines running, put them on, and every playing
 * sound of the world must have had the muffle put on its source (with no OpenAL error from doing it), while a sound of an
 * exempt category must not; take them off and none may. OpenAL cannot read a source's filter back, so what is checked is the
 * calls the sound thread made and that the card took each without an error, not what is heard. The wearing is done the way a player does it, on the server's player, so the client's own tick has to notice
 * it. A sound card without the OpenAL filter extension is judged by the fallback (a loudness cap) instead.
 */
final class HeadphonesAudit {

	private static int changedBefore;

	private HeadphonesAudit() {
	}

	/** Looks at the player from the front, or back through their eyes, so a picture can show the headphones on their head. */
	static void frontView(boolean front) {
		Minecraft.getInstance().options.setCameraType(front ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
	}

	/** Keeps a picture of the frame for a person to look at (it is not judged: what is on the head is for eyes). */
	static void shoot(String name) {
		try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance()
			.getMainRenderTarget())) {
			Path shots = Path.of("smoke", "client", "shots");
			Files.createDirectories(shots);
			image.writeToFile(shots.resolve(name + ".png"));
		} catch (IOException e) {
			CreateEngines.LOGGER.warn("Could not keep screenshot {}: {}", name, e.getMessage());
		}
	}

	/** Puts the headphones on the player's head, or takes them off. */
	static void wear(boolean on) {
		com.createengines.client.VisualSmokeTest.server(server -> {
			var player = server.getPlayerList()
				.getPlayers()
				.get(0);
			player.setItemSlot(EquipmentSlot.HEAD, on ? new ItemStack(CEItems.HEADPHONES.get()) : ItemStack.EMPTY);
		});
	}

	/** Worn: every playing world sound is muffled, no exempt one is, and the card took the calls without an error. */
	static void checkWorn(SmokeReport report) {
		changedBefore = HeadphoneMuffle.sourcesChanged();
		report.check("headphones", "the client notices the headphones going on", HeadphoneMuffle.worn(),
			"the headphones are on the player's head but the client has not seen them worn");
		var audit = HeadphoneMuffle.audit();
		report.check("headphones", "engines are playing sounds of a muffled category to test against", audit.muffled() > 0,
			"no playing sound of a category the headphones muffle; the fleet should be running");
		report.check("headphones", "every playing world sound has had the muffle put on it (" + audit.muffledNow() + " of "
			+ audit.muffled() + ", " + (HeadphoneMuffle.filtering() ? "low-pass filter" : "loudness cap") + ")",
			audit.muffledNow() == audit.muffled(), audit.muffledNow() + " of " + audit.muffled() + " world sounds are muffled");
		report.check("headphones", "no sound of an exempt category is muffled (" + audit.exemptMuffled() + " of " + audit.exempt() + ")",
			audit.exemptMuffled() == 0, audit.exemptMuffled() + " of " + audit.exempt() + " exempt sounds are muffled");
		report.check("headphones", "putting them on left no OpenAL error", HeadphoneMuffle.sourcesFailed() == 0,
			HeadphoneMuffle.sourcesFailed() + " sources failed with an OpenAL error");
		if (!HeadphoneMuffle.filtering())
			report.warn("headphones", "this sound device has no OpenAL filter extension, so the low-pass filter was not tried",
				"the headphones fell back to a loudness cap");
	}

	/** Taken off: no sound is muffled, and the sources that had the muffle were changed back. */
	static void checkRemoved(SmokeReport report) {
		report.check("headphones", "the client notices the headphones coming off", !HeadphoneMuffle.worn(),
			"the headphones are off but the client still sees them worn");
		var audit = HeadphoneMuffle.audit();
		report.check("headphones", "no playing sound is muffled once they are off (" + audit.muffledNow() + " of " + audit.muffled() + ")",
			audit.muffledNow() == 0 && audit.exemptMuffled() == 0, audit.muffledNow() + " world sounds and " + audit.exemptMuffled()
				+ " exempt sounds are still muffled");
		report.check("headphones", "taking them off changed the sources that had them on", HeadphoneMuffle.sourcesChanged() > changedBefore,
			"no source was touched when they came off");
		report.check("headphones", "taking them off left no OpenAL error", HeadphoneMuffle.sourcesFailed() == 0,
			HeadphoneMuffle.sourcesFailed() + " sources failed with an OpenAL error");
	}
}
