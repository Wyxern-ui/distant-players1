package com.example.distantplayers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

public class DistantPlayers implements ModInitializer {
	public static final String MOD_ID = "distantplayers";

	/** Players closer than this are left to vanilla rendering. Keep <= the smallest client render limit. */
	public static final double MIN_DISTANCE = 16.0;
	/** Players farther than this are never sent. */
	public static final double MAX_DISTANCE = 512.0;
	/** Run the line-of-sight pass every N server ticks. */
	public static final int INTERVAL_TICKS = 2;

	/** Viewers that were sent a non-empty list last time (so we know to send one empty list to clear it). */
	private final Set<UUID> viewersWithGhosts = new HashSet<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(DistantPlayersPayload.TYPE, DistantPlayersPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL_TICKS != 0) return;
			for (ServerLevel level : server.getAllLevels()) {
				List<ServerPlayer> players = level.players();
				for (ServerPlayer viewer : players) {
					if (!ServerPlayNetworking.canSend(viewer, DistantPlayersPayload.TYPE)) continue;

					List<DistantPlayersPayload.Entry> list = new ArrayList<>();
					for (ServerPlayer target : players) {
						if (target == viewer || target.isSpectator() || target.isInvisible()) continue;
						double distSq = viewer.distanceToSqr(target);
						if (distSq < MIN_DISTANCE * MIN_DISTANCE || distSq > MAX_DISTANCE * MAX_DISTANCE) continue;
						if (!canSee(level, viewer, target)) continue;
						list.add(new DistantPlayersPayload.Entry(target.getUUID(),
								target.getX(), target.getY(), target.getZ(),
								target.getYRot(), target.getXRot(),
								target.isCrouching(), target.isSprinting()));
					}

					boolean had = viewersWithGhosts.contains(viewer.getUUID());
					if (!list.isEmpty()) {
						viewersWithGhosts.add(viewer.getUUID());
						ServerPlayNetworking.send(viewer, new DistantPlayersPayload(list));
					} else if (had) {
						viewersWithGhosts.remove(viewer.getUUID());
						ServerPlayNetworking.send(viewer, new DistantPlayersPayload(list));
					}
				}
			}
		});
	}

	private static boolean canSee(ServerLevel level, ServerPlayer a, ServerPlayer b) {
		Vec3 from = a.getEyePosition();
		return rayClear(level, from, b.getEyePosition())
				|| rayClear(level, from, b.position().add(0, 0.2, 0));
	}

	/**
	 * Voxel walk between two points. Uses getChunkNow so it never loads or generates chunks;
	 * unloaded chunks are treated as empty air.
	 */
	private static boolean rayClear(ServerLevel level, Vec3 from, Vec3 to) {
		Boolean blocked = BlockGetter.traverseBlocks(from, to, level, (lvl, pos) -> {
			LevelChunk chunk = lvl.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			if (chunk == null) return null;
			boolean solid = !chunk.getBlockState(pos).getCollisionShape(chunk, pos).isEmpty();
			return solid ? Boolean.TRUE : null;
		}, lvl -> Boolean.FALSE);
		return !blocked;
	}
}
