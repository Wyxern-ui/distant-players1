package com.example.distantplayers.client;

import com.example.distantplayers.DistantPlayersPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DistantPlayersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(DistantPlayersPayload.TYPE,
				(payload, context) -> GhostManager.accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(GhostManager::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GhostManager.reset());
	}
}
