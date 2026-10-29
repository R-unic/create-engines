package com.createengines.registry;

import static com.createengines.CreateEngines.REGISTRATE;

import com.createengines.content.EnginePartRenderers;
import com.createengines.content.camshaft.CamshaftBlockEntity;
import com.createengines.content.camshaft.CamshaftVisual;
import com.createengines.content.crankshaft.CrankshaftBlockEntity;
import com.createengines.content.crankshaft.CrankshaftVisual;
import com.createengines.content.cylinder.CylinderBlockEntity;
import com.createengines.content.cylinder.CylinderVisual;
import com.createengines.content.head.CylinderHeadBlockEntity;
import com.createengines.content.head.CylinderHeadVisual;
import com.createengines.content.starter.StarterBlockEntity;
import com.createengines.content.starter.StarterRenderer;
import com.createengines.content.starter.StarterVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class CEBlockEntities {

	public static final BlockEntityEntry<CrankshaftBlockEntity> CRANKSHAFT = REGISTRATE
		.blockEntity("crankshaft", CrankshaftBlockEntity::new)
		.visual(() -> CrankshaftVisual::new, false)
		.validBlocks(CEBlocks.CRANKSHAFT, CEBlocks.CRANK_THROW)
		.renderer(() -> EnginePartRenderers.Crankshaft::new)
		.register();

	public static final BlockEntityEntry<CamshaftBlockEntity> CAMSHAFT = REGISTRATE
		.blockEntity("camshaft", CamshaftBlockEntity::new)
		.visual(() -> CamshaftVisual::new, false)
		.validBlocks(CEBlocks.CAMSHAFT, CEBlocks.CAM_LOBE)
		.renderer(() -> EnginePartRenderers.Camshaft::new)
		.register();

	public static final BlockEntityEntry<CylinderBlockEntity> CYLINDER = REGISTRATE
		.blockEntity("cylinder", CylinderBlockEntity::new)
		.visual(() -> CylinderVisual::new)
		.validBlocks(CEBlocks.CYLINDER)
		.renderer(() -> EnginePartRenderers.Cylinder::new)
		.register();

	public static final BlockEntityEntry<CylinderHeadBlockEntity> CYLINDER_HEAD = REGISTRATE
		.blockEntity("cylinder_head", CylinderHeadBlockEntity::new)
		.visual(() -> CylinderHeadVisual::new)
		.validBlocks(CEBlocks.CYLINDER_HEAD)
		.renderer(() -> EnginePartRenderers.Head::new)
		.register();

	public static final BlockEntityEntry<StarterBlockEntity> STARTER = REGISTRATE
		.blockEntity("starter", StarterBlockEntity::new)
		.visual(() -> StarterVisual::create, false)
		.validBlocks(CEBlocks.STARTER)
		.renderer(() -> StarterRenderer::new)
		.register();

	private CEBlockEntities() {
	}

	public static void register() {
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CYLINDER_HEAD.get(),
			(be, side) -> be.fluidCapability());
	}
}
