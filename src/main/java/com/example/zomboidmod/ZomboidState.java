package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Всё, что мод хранит в мире: статы игроков, навыки, обработанные регионы городов. */
public class ZomboidState extends PersistentState {
    public final Map<UUID, PlayerStats> players = new HashMap<>();
    public final Set<Long> townRegions = new HashSet<>();

    private static final PersistentState.Type<ZomboidState> TYPE =
            new PersistentState.Type<>(ZomboidState::new, ZomboidState::fromNbt, null);

    public static ZomboidState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ZomboidMod.MOD_ID);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound all = new NbtCompound();
        players.forEach((id, s) -> all.put(id.toString(), s.toNbt()));
        nbt.put("players", all);
        nbt.putLongArray("towns", townRegions.stream().mapToLong(Long::longValue).toArray());
        return nbt;
    }

    public static ZomboidState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ZomboidState st = new ZomboidState();
        NbtCompound all = nbt.getCompound("players");
        for (String key : all.getKeys()) {
            st.players.put(UUID.fromString(key), PlayerStats.fromNbt(all.getCompound(key)));
        }
        for (long l : nbt.getLongArray("towns")) st.townRegions.add(l);
        return st;
    }
}
