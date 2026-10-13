package com.example.distantplayers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: players the receiver has a clear line of sight to. */
public record DistantPlayersPayload(List<Entry> entries) implements CustomPacketPayload {

	public static final Type<DistantPlayersPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(DistantPlayers.MOD_ID, "distant_players"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DistantPlayersPayload> CODEC =
			CustomPacketPayload.codec(DistantPlayersPayload::write, DistantPlayersPayload::read);

	public record Entry(UUID id, double x, double y, double z,
						float yRot, float xRot, boolean crouching, boolean sprinting) {
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (Entry e : entries) {
			buf.writeUUID(e.id());
			buf.writeDouble(e.x());
			buf.writeDouble(e.y());
			buf.writeDouble(e.z());
			buf.writeFloat(e.yRot());
			buf.writeFloat(e.xRot());
			buf.writeBoolean(e.crouching());
			buf.writeBoolean(e.sprinting());
		}
	}

	private static DistantPlayersPayload read(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Entry> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(new Entry(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));
		}
		return new DistantPlayersPayload(list);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
