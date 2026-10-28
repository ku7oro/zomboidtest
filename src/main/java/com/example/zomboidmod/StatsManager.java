package com.example.zomboidmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class StatsManager {
    public static final int MAX = 100;

    // Настройки (в секундах)
    private static final int THIRST_DROP_EVERY = 12;
    private static final int FATIGUE_DROP_EVERY = 15;
    private static final int BLEED_DAMAGE_EVERY = 5;

    // Заражение после укуса зомби
    public static final boolean INFECTION_ENABLED = true;
    private static final int INFECTION_WEAK_AT = 180;
    private static final int INFECTION_SICK_AT = 360;
    private static final int INFECTION_DEATH_AT = 600;

    private static final int SPRINT_NOISE_RADIUS = 14;

    // Вес инвентаря: базовый лимит + 4 за каждый уровень Силы
    public static final int CAPACITY = 60;

    // Сохранять ли навыки после смерти
    public static final boolean KEEP_SKILLS_ON_DEATH = true;

    public static PlayerStats get(ServerPlayerEntity p) {
        return ZomboidState.get(p.getServer()).players.computeIfAbsent(p.getUuid(), k -> new PlayerStats());
    }

    public static void reset(ServerPlayerEntity p) {
        ZomboidState st = ZomboidState.get(p.getServer());
        PlayerStats old = st.players.get(p.getUuid());
        PlayerStats fresh = new PlayerStats();
        if (KEEP_SKILLS_ON_DEATH && old != null) {
            fresh.xp = old.xp.clone();
            fresh.level = old.level.clone();
        }
        st.players.put(p.getUuid(), fresh);
        st.markDirty();
    }

    public static int capacity(PlayerStats s) {
        return CAPACITY + 4 * Skills.level(s, Skill.STRENGTH);
    }

    public static int computeWeight(ServerPlayerEntity p) {
        double w = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            Item it = st.getItem();
            double unit;
            if (it instanceof BlockItem) unit = 0.8;
            else if (it instanceof ToolItem || it instanceof ArmorItem) unit = 3.0;
            else unit = 0.1;
            w += unit * st.getCount();
        }
        return (int) Math.round(w);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) return; // раз в секунду

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            PlayerStats s = get(p);
            int fitness = Skills.level(s, Skill.FITNESS);

            // Бег: шум + опыт выносливости
            if (p.isSprinting()) {
                Noise.emit(p, SPRINT_NOISE_RADIUS);
                Skills.addXp(p, Skill.FITNESS, 2);
            }

            // Скрытность: опыт за хождение на корточках рядом с зомби
            if (p.isSneaking() && !p.isSprinting()
                    && !p.getWorld().getEntitiesByClass(ZombieEntity.class,
                    p.getBoundingBox().expand(24), z -> z.isAlive()).isEmpty()) {
                Skills.addXp(p, Skill.STEALTH, 2);
            }

            // Жажда (с 5 уровня выносливости бег не ускоряет жажду)
            s.thirstTimer += (p.isSprinting() && fitness < 5) ? 2 : 1;
            if (s.thirstTimer >= THIRST_DROP_EVERY) {
                s.thirstTimer = 0;
                s.thirst = Math.max(0, s.thirst - 1);
            }

            // Усталость (выносливость замедляет)
            s.fatigueTimer++;
            if (s.fatigueTimer >= FATIGUE_DROP_EVERY + fitness) {
                s.fatigueTimer = 0;
                s.fatigue = Math.max(0, s.fatigue - 1);
            }

            if (s.thirst <= 0 && server.getTicks() % 80 == 0) {
                p.damage(p.getDamageSources().starve(), 1.0f);
            }

            if (s.fatigue <= 20) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0, true, false));
            }
            if (s.fatigue <= 0) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 60, 0, true, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, true, false));
            }

            if (s.bleeding) {
                s.bleedTimer++;
                if (s.bleedTimer >= BLEED_DAMAGE_EVERY) {
                    s.bleedTimer = 0;
                    p.damage(p.getDamageSources().generic(), 1.0f);
                }
            } else {
                s.bleedTimer = 0;
            }

            if (INFECTION_ENABLED && s.infected) {
                s.infectionTimer++;
                if (s.infectionTimer >= INFECTION_WEAK_AT) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, true, false));
                }
                if (s.infectionTimer >= INFECTION_SICK_AT) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, true, false));
                    if (s.infectionTimer % 10 == 0) {
                        p.damage(p.getDamageSources().generic(), 1.0f);
                    }
                }
                if (s.infectionTimer >= INFECTION_DEATH_AT) {
                    p.damage(p.getDamageSources().genericKill(), Float.MAX_VALUE);
                }
            }

            int cap = capacity(s);
            int weight = computeWeight(p);
            if (weight > cap) {
                int amp = weight > cap * 2 ? 2 : (weight > cap * 3 / 2 ? 1 : 0);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, amp, true, false));
            }

            ServerPlayNetworking.send(p, new StatsPayload(s.thirst, s.fatigue, s.bleeding, s.infected, weight, cap));
        }

        ZomboidState.get(server).markDirty();
    }
}
