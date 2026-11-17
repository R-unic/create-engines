package com.createengines.client;

import com.createengines.content.blueprint.BlueprintImport;
import com.createengines.content.blueprint.RecordedBlueprint;
import com.createengines.registry.CELang;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A box to paste a shared blueprint into, and a button to send it to the server, which checks it and stores it in the
 * blueprint in the player's hand. The client checks nothing: the server decides. Also where a blueprint is copied out of
 * ({@link #copy}). Client only.
 */
public class BlueprintImportScreen extends Screen {

	private EditBox box;

	public static void open() {
		Minecraft.getInstance()
			.setScreen(new BlueprintImportScreen());
	}

	/** Puts the blueprint's string on the clipboard and says so. */
	public static void copy(RecordedBlueprint recorded) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.keyboardHandler.setClipboard(recorded.text());
		if (minecraft.player != null)
			minecraft.player.displayClientMessage(CELang.translate("blueprint.copied")
				.component(), true);
	}

	/** A dialog that is not shown yet, for the client audit to draw. */
	public static BlueprintImportScreen create() {
		return new BlueprintImportScreen();
	}

	private BlueprintImportScreen() {
		super(CELang.translate("blueprint.import.title")
			.component());
	}

	@Override
	protected void init() {
		int middle = width / 2;
		box = new EditBox(font, middle - 150, height / 2 - 10, 300, 20, title);
		box.setMaxLength(BlueprintImport.MAX_TEXT);
		addRenderableWidget(box);
		setInitialFocus(box);
		addRenderableWidget(Button.builder(CELang.translate("blueprint.import.button")
			.component(), button -> send())
			.bounds(middle - 152, height / 2 + 18, 100, 20)
			.build());
		addRenderableWidget(Button.builder(CELang.translate("blueprint.import.cancel")
			.component(), button -> onClose())
			.bounds(middle + 52, height / 2 + 18, 100, 20)
			.build());
	}

	private void send() {
		String text = box.getValue();
		if (!text.isBlank())
			PacketDistributor.sendToServer(new BlueprintImport.Payload(text.strip()));
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
		graphics.drawCenteredString(font, CELang.translate("blueprint.import.hint")
			.component(), width / 2, height / 2 - 26, 0xFFA0A0A0);
	}
}
