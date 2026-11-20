package com.createengines.client.compat;

import com.createengines.registry.CEItems;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws the headphones on the head of someone wearing them in a Curios slot, as the game draws a head-slot item: from the
 * item's own head display transform, on the model's head. Names Curios' types, so it is only loaded with Curios there.
 */
public final class CuriosClient {

	private CuriosClient() {
	}

	public static void register() {
		CuriosRendererRegistry.register(CEItems.HEADPHONES.get(), HeadphonesRenderer::new);
	}

	private static final class HeadphonesRenderer implements ICurioRenderer {

		@Override
		public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slot,
			PoseStack poseStack, RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing,
			float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
			if (!(parent.getModel() instanceof HeadedModel headed))
				return;
			LivingEntity wearer = slot.entity();
			poseStack.pushPose();
			headed.getHead()
				.translateAndRotate(poseStack);
			// The same placing as the game's own head-slot layer: up to the middle of the head, turned about, one block
			// of model for ten pixels of head
			poseStack.translate(0.0F, -0.25F, 0.0F);
			poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F));
			poseStack.scale(0.625F, -0.625F, -0.625F);
			Minecraft.getInstance()
				.getItemRenderer()
				.renderStatic(wearer, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, wearer.level(), light,
					OverlayTexture.NO_OVERLAY, wearer.getId());
			poseStack.popPose();
		}
	}
}
