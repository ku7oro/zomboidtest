package com.example.zomboidmod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StatsPayload(int thirst, int fatigue, boolean bleeding, boolean infected, int weight, int capacity)
        implements CustomPayload {
    public static final CustomPayload.Id<StatsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ZomboidMod.MOD_ID, "stats"));

    public static final PacketCodec<RegistryByteBuf, StatsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, StatsPayload::thirst,
            PacketCodecs.INTEGER, StatsPayload::fatigue,
            PacketCodecs.BOOL, StatsPayload::bleeding,
            PacketCodecs.BOOL, StatsPayload::infected,
            PacketCodecs.INTEGER, StatsPayload::weight,
            PacketCodecs.INTEGER, StatsPayload::capacity,
            StatsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
