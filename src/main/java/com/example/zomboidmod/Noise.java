package com.example.zomboidmod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Система шума: зомби идут на звук (бег, ломание блоков, драки). */
public class Noise {
    private record Target(ServerWorld world, BlockPos pos, long expires) {}

    private static final Map<UUID, Target> TARGETS = new HashMap<>();
    private static final int MEMORY_TICKS = 200; // 10 секунд помнят место шума

    /** Шум от игрока: прокачанная скрытность уменьшает радиус (до -50%). */
    public static void emit(ServerPlayerEntity p, int radius) {
        double mult = 1.0 - 0.05 * Skills.level(p, Skill.STEALTH);
        emit(p.getWorld(), p.getBlockPos(), (int) Math.round(radius * mult));
    }

    public static void emit(World world, BlockPos pos, int radius) {
        if (!(world instanceof ServerWorld sw)) return;
        Box box = new Box(pos).expand(radius);
        for (ZombieEntity z : sw.getEntitiesByClass(ZombieEntity.class, box, e -> e.isAlive())) {
            TARGETS.put(z.getUuid(), new Target(sw, pos.toImmutable(), sw.getTime() + MEMORY_TICKS));
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 0) return;
        Iterator<Map.Entry<UUID, Target>> it = TARGETS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Target> en = it.next();
            Target t = en.getValue();
            Entity e = t.world().getEntity(en.getKey());
            if (!(e instanceof ZombieEntity z) || !z.isAlive() || t.world().getTime() > t.expires()) {
                it.remove();
                continue;
            }
            if (z.getTarget() != null) continue; // уже видит цель
            if (z.getBlockPos().getSquaredDistance(t.pos()) < 4) {
                it.remove(); // дошёл до источника шума
                continue;
            }
            z.getNavigation().startMovingTo(t.pos().getX() + 0.5, t.pos().getY(), t.pos().getZ() + 0.5, 1.0);
        }
    }
}
