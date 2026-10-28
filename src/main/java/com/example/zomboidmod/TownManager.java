package com.example.zomboidmod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/**
 * Автогенерация: мир разбит на регионы REGION x REGION. По сиду мира каждый регион
 * детерминированно либо содержит город, либо нет. Город строится один раз,
 * когда игрок подходит к нему ближе TRIGGER блоков.
 */
public class TownManager {
    private static final int REGION = 384;
    private static final float TOWN_CHANCE = 0.4f;
    private static final int TRIGGER = 80;

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ServerWorld world = server.getOverworld();
        ZomboidState state = ZomboidState.get(server);

        for (ServerPlayerEntity p : world.getPlayers()) {
            int prx = Math.floorDiv(p.getBlockX(), REGION);
            int prz = Math.floorDiv(p.getBlockZ(), REGION);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int rx = prx + dx, rz = prz + dz;
                    long key = ((long) rx << 32) | (rz & 0xffffffffL);
                    if (state.townRegions.contains(key)) continue;

                    java.util.Random r = new java.util.Random(
                            world.getSeed() * 341873128712L + rx * 132897987541L + rz * 42317861L);
                    if (r.nextFloat() >= TOWN_CHANCE) {
                        state.townRegions.add(key);
                        state.markDirty();
                        continue;
                    }

                    int cells = 3 + r.nextInt(3);
                    int span = cells * 16;
                    int ox = rx * REGION + 16 + r.nextInt(REGION - span - 32);
                    int oz = rz * REGION + 16 + r.nextInt(REGION - span - 32);
                    double cx = ox + span / 2.0, cz = oz + span / 2.0;
                    if (Math.hypot(p.getX() - cx, p.getZ() - cz) > TRIGGER) continue;

                    state.townRegions.add(key);
                    state.markDirty();
                    if (!isSuitable(world, ox, oz, span)) continue;

                    String name = TownGenerator.build(world, new BlockPos(ox, 0, oz), cells);
                    p.sendMessage(Text.literal("Неподалёку заброшенный город «" + name + "»"), false);
                    return; // не больше одного города за проверку
                }
            }
        }
    }

    /** Не строим на воде и на крутых склонах. */
    private static boolean isSuitable(ServerWorld world, int ox, int oz, int span) {
        int[][] pts = {{ox, oz}, {ox + span, oz}, {ox, oz + span}, {ox + span, oz + span}, {ox + span / 2, oz + span / 2}};
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] pt : pts) {
            int h = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pt[0], pt[1]);
            if (!world.getFluidState(new BlockPos(pt[0], h - 1, pt[1])).isEmpty()) return false;
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return max - min <= 8;
    }
}
