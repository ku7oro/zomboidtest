package com.example.zomboidmod;

import net.minecraft.nbt.NbtCompound;

public class PlayerStats {
    public int thirst = StatsManager.MAX;
    public int fatigue = StatsManager.MAX;
    public boolean bleeding = false;
    public boolean infected = false;
    public int infectionTimer = 0; // секунды с момента укуса
    public int[] xp = new int[Skill.values().length];
    public int[] level = new int[Skill.values().length];

    // временные счётчики (не сохраняются)
    public int thirstTimer = 0;
    public int fatigueTimer = 0;
    public int bleedTimer = 0;

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("thirst", thirst);
        n.putInt("fatigue", fatigue);
        n.putBoolean("bleeding", bleeding);
        n.putBoolean("infected", infected);
        n.putInt("infectionTimer", infectionTimer);
        n.putIntArray("xp", xp);
        n.putIntArray("lvl", level);
        return n;
    }

    public static PlayerStats fromNbt(NbtCompound n) {
        PlayerStats s = new PlayerStats();
        s.thirst = n.getInt("thirst");
        s.fatigue = n.getInt("fatigue");
        s.bleeding = n.getBoolean("bleeding");
        s.infected = n.getBoolean("infected");
        s.infectionTimer = n.getInt("infectionTimer");
        int[] xp = n.getIntArray("xp");
        int[] lvl = n.getIntArray("lvl");
        if (xp.length == s.xp.length) s.xp = xp;
        if (lvl.length == s.level.length) s.level = lvl;
        return s;
    }
}
