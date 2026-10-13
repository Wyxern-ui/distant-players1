package com.example.distantplayers.client;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * A client-only stand-in for a far-away player. It has a random UUID (so it never clashes with the
 * real player entity if that one is also loaded) and borrows the real player's skin.
 */
public class GhostPlayer extends RemotePlayer {
	private final PlayerInfo skinSource;

	public GhostPlayer(ClientLevel level, GameProfile ghostProfile, PlayerInfo skinSource) {
		super(level, ghostProfile);
		this.skinSource = skinSource;
		this.noPhysics = true;
		this.setNoGravity(true);
		// show all skin layers (jacket, sleeves, hat, ...)
		this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
	}

	@Override
	public PlayerSkin getSkin() {
		return skinSource.getSkin();
	}

	/** Vanilla culls entities past ~64 blocks; the ghost sits close to the camera, so never cull it. */
	@Override
	public boolean shouldRender(double x, double y, double z) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
