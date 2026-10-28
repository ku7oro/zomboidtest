package com.example.zomboidmod;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class Skills {
    public static final int MAX_LEVEL = 10;

    /** Сколько опыта нужно, чтобы перейти с уровня level на level+1. */
    public static int need(int level) {
        return 100 * (level + 1);
    }

    public static int level(PlayerStats s, Skill sk) {
        return s.level[sk.ordinal()];
    }

    public static int level(ServerPlayerEntity p, Skill sk) {
        return level(StatsManager.get(p), sk);
    }

    public static void addXp(ServerPlayerEntity p, Skill sk, int amount) {
        PlayerStats s = StatsManager.get(p);
        int i = sk.ordinal();
        if (s.level[i] >= MAX_LEVEL) return;
        s.xp[i] += amount;
        while (s.level[i] < MAX_LEVEL && s.xp[i] >= need(s.level[i])) {
            s.xp[i] -= need(s.level[i]);
            s.level[i]++;
            p.sendMessage(Text.literal("Навык «" + sk.title + "» повышен до уровня " + s.level[i]), true);
        }
    }
}
