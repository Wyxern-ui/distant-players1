package com.example.distantplayers.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.distantplayers.DistantPlayersPayload;
import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Core trick: for a player at real distance d beyond the render limit, draw a copy at distance
 * "near" along the same line from the camera, scaled by near/d. Same angular size, same direction,
 * but inside the range vanilla actually renders.
 */
public final class GhostManager {
	private static final double SMOOTHING = 0.5;   // fraction of the gap closed per client tick
	private static final int TIMEOUT_TICKS = 40;   // drop ghosts if the server goes quiet

	private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();
	private static List<DistantPlayersPayload.Entry> latest = List.of();
	private static int ticksSincePacket = 0;
	private static ClientLevel currentLevel = null;
	private static int nextId = -1_000_000;

	private static final class Ghost {
		final GhostPlayer entity;
		Vec3 smoothedReal;

		Ghost(GhostPlayer entity, Vec3 real) {
			this.entity = entity;
			this.smoothedReal = real;
		}
	}

	private GhostManager() {
	}

	public static void accept(DistantPlayersPayload payload) {
		latest = payload.entries();
		ticksSincePacket = 0;
	}

	public static void reset() {
		GHOSTS.clear(); // entities die with their ClientLevel
		latest = List.of();
		currentLevel = null;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer me = mc.player;
		if (level == null || me == null) {
			reset();
			return;
		}
		if (level != currentLevel) { // joined a world / changed dimension
			GHOSTS.clear();
			latest = List.of();
			currentLevel = level;
		}
		if (++ticksSincePacket > TIMEOUT_TICKS) latest = List.of();

		Vec3 cam = me.getEyePosition();
		double near = nearDistance(mc);
		Set<UUID> active = new HashSet<>();

		for (DistantPlayersPayload.Entry e : latest) {
			Vec3 real = new Vec3(e.x(), e.y(), e.z());
			if (real.distanceTo(cam) <= near) continue; // vanilla renders this one

			Ghost ghost = GHOSTS.get(e.id());
			if (ghost == null) {
				ghost = create(mc, level, e, real);
				if (ghost == null) continue;
				GHOSTS.put(e.id(), ghost);
			}

			ghost.smoothedReal = ghost.smoothedReal.lerp(real, SMOOTHING);
			Vec3 offset = ghost.smoothedReal.subtract(cam);
			double dist = Math.max(offset.length(), 1.0E-4);
			double k = Math.min(1.0, near / dist);

			GhostPlayer g = ghost.entity;
			AttributeInstance scale = g.getAttribute(Attributes.SCALE);
			if (scale != null && scale.getBaseValue() != k) scale.setBaseValue(k);

			g.setPos(cam.add(offset.scale(k)));
			g.setYRot(e.yRot());
			g.setXRot(e.xRot());
			g.setYHeadRot(e.yRot());
			g.setShiftKeyDown(e.crouching());
			g.setSprinting(e.sprinting());
			active.add(e.id());
		}

		Iterator<Map.Entry<UUID, Ghost>> it = GHOSTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Ghost> entry = it.next();
			if (!active.contains(entry.getKey())) {
				level.removeEntity(entry.getValue().entity.getId(), Entity.RemovalReason.DISCARDED);
				it.remove();
			}
		}
	}

	private static Ghost create(Minecraft mc, ClientLevel level, DistantPlayersPayload.Entry e, Vec3 real) {
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return null;
		PlayerInfo info = conn.getPlayerInfo(e.id());
		if (info == null) return null; // not in the tab list (yet)

		GameProfile ghostProfile = new GameProfile(UUID.randomUUID(), info.getProfile().name());
		GhostPlayer entity = new GhostPlayer(level, ghostProfile, info);
		entity.setId(nextId--);
		entity.setPos(real);
		entity.setOldPosAndRot();
		entity.yBodyRot = entity.yBodyRotO = e.yRot();
		entity.yHeadRot = entity.yHeadRotO = e.yRot();
		level.addEntity(entity);
		return new Ghost(entity, real);
	}

	/** Distance at which the ghost is parked: the edge of what vanilla will actually draw. */
	private static double nearDistance(Minecraft mc) {
		Options o = mc.options;
		double rd = o.getEffectiveRenderDistance();
		// vanilla: entities cull at ~64 * clamp(rd/8, 1, 2.5) * entityDistanceScaling
		double entityLimit = 64.0 * Mth.clamp(rd / 8.0, 1.0, 2.5) * o.entityDistanceScaling().get();
		double chunkLimit = rd * 16.0 - 16.0; // stay inside loaded chunks
		return Math.max(16.0, Math.min(entityLimit, chunkLimit));
	}
}
