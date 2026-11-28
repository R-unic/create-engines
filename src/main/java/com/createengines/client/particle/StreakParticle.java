package com.createengines.client.particle;

import org.joml.Vector3f;

import com.createengines.content.effects.FlameMotion;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;

/**
 * A burning thing drawn along the way it is going: its sprite is stretched along its velocity (the top of the picture
 * leads) and turned about that line to face the camera. Still, or coming straight at the camera, it is a square facing
 * the camera. As bright as fire whatever the light.
 */
abstract class StreakParticle extends TextureSheetParticle {

	protected StreakParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
							 double zSpeed) {
		super(level, x, y, z);
		xd = xSpeed;
		yd = ySpeed;
		zd = zSpeed;
	}

	/** Half the length of the streak for a particle of half width {@code width} moving at {@code speed}. */
	protected abstract double halfLength(double width, double speed);

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getLightColor(float partialTick) {
		return LightTexture.FULL_BRIGHT;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTick) {
		Vec3 eye = camera.getPosition();
		double px = xo + (x - xo) * partialTick - eye.x;
		double py = yo + (y - yo) * partialTick - eye.y;
		double pz = zo + (z - zo) * partialTick - eye.z;
		double width = getQuadSize(partialTick);
		double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
		double[][] axes = FlameMotion.streakAxes(new double[] {xd, yd, zd}, new double[] {-px, -py, -pz},
			halfLength(width, speed), width);
		if (axes == null) {
			Vector3f up = camera.getUpVector();
			Vector3f left = camera.getLeftVector();
			axes = new double[][] {{up.x * width, up.y * width, up.z * width},
				{-left.x * width, -left.y * width, -left.z * width}};
		}
		double[] along = axes[0];
		double[] across = axes[1];
		int light = getLightColor(partialTick);
		corner(buffer, px, py, pz, along, across, -1, -1, getU0(), getV1(), light);
		corner(buffer, px, py, pz, along, across, -1, 1, getU1(), getV1(), light);
		corner(buffer, px, py, pz, along, across, 1, 1, getU1(), getV0(), light);
		corner(buffer, px, py, pz, along, across, 1, -1, getU0(), getV0(), light);
	}

	private void corner(VertexConsumer buffer, double x, double y, double z, double[] along, double[] across, int forth,
						int side, float u, float v, int light) {
		buffer.addVertex((float) (x + along[0] * forth + across[0] * side),
				(float) (y + along[1] * forth + across[1] * side), (float) (z + along[2] * forth + across[2] * side))
			.setUv(u, v)
			.setColor(rCol, gCol, bCol, alpha)
			.setLight(light);
	}
}
