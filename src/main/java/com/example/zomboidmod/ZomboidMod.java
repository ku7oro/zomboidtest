package com.example.zomboidmod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

public class ZomboidMod implements ModInitializer {
    public static final String MOD_ID = "zomboidmod";

    // Зомби "плохо видят": дальность обнаружения (ванильная = 35)
    private static final double ZOMBIE_SIGHT = 12.0;
    private static final float BITE_CHANCE = 0.25f;
    private static final float BLEED_CHANCE = 0.30f;

    public static Item BANDAGE;
    public static Item CLEAN_WATER;
    public static Item CANNED_BEANS;
    public static Item PAINKILLERS;
    public static Item NAIL_BAT;

    @Override
    public void onInitialize() {
        BANDAGE = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "bandage"),
                new BandageItem(new Item.Settings().maxCount(16)));
        CLEAN_WATER = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "clean_water"),
                new CleanWaterItem(new Item.Settings().maxCount(16)));
        CANNED_BEANS = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "canned_beans"),
                new Item(new Item.Settings().maxCount(16)
                        .food(new FoodComponent.Builder().nutrition(6).saturationModifier(0.6f).build())));
        PAINKILLERS = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "painkillers"),
                new PainkillersItem(new Item.Settings().maxCount(16)));
        NAIL_BAT = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "nail_bat"),
                new SwordItem(ToolMaterials.WOOD, new Item.Settings()
                        .attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.WOOD, 5, -2.8f))));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(e -> {
            e.add(CANNED_BEANS);
            e.add(CLEAN_WATER);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(BANDAGE);
            e.add(PAINKILLERS);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> e.add(NAIL_BAT));

        PayloadTypeRegistry.playS2C().register(StatsPayload.ID, StatsPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(StatsManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(Noise::tick);
        ServerTickEvents.END_SERVER_TICK.register(TownManager::tick);

        // Урон от существ: кровотечение и укус зомби
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && source.getAttacker() instanceof LivingEntity attacker) {
                PlayerStats s = StatsManager.get(p);
                if (amount >= 2.0f && p.getRandom().nextFloat() < BLEED_CHANCE) {
                    s.bleeding = true;
                }
                if (StatsManager.INFECTION_ENABLED && attacker instanceof ZombieEntity
                        && !s.infected && p.getRandom().nextFloat() < BITE_CHANCE) {
                    s.infected = true;
                    s.infectionTimer = 0;
                }
            }
            return true;
        });

        EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
            if (entity instanceof ServerPlayerEntity p) {
                StatsManager.get(p).fatigue = StatsManager.MAX;
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> StatsManager.reset(newPlayer));

        // Шум и опыт силы
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayerEntity sp) Noise.emit(sp, 16);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                Noise.emit(sp, 20);
                if (entity instanceof LivingEntity) Skills.addXp(sp, Skill.STRENGTH, 3);
            }
            return ActionResult.PASS;
        });

        // Зомби видят ближе (ванильные 35 -> ZOMBIE_SIGHT)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ZombieEntity z) {
                EntityAttributeInstance attr = z.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
                if (attr != null) attr.setBaseValue(ZOMBIE_SIGHT);
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Админские команды (нужны права оператора)
            dispatcher.register(CommandManager.literal("zomboid")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(CommandManager.literal("cure").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        PlayerStats s = StatsManager.get(p);
                        s.infected = false;
                        s.infectionTimer = 0;
                        s.bleeding = false;
                        ctx.getSource().sendFeedback(() -> Text.literal("Вылечен"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("town").executes(ctx -> {
                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                        ServerWorld w = ctx.getSource().getWorld();
                        String name = TownGenerator.build(w, p.getBlockPos().add(4, 0, 4), 4);
                        ctx.getSource().sendFeedback(() -> Text.literal("Построен город «" + name + "»"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("reset").executes(ctx -> {
                        StatsManager.reset(ctx.getSource().getPlayerOrThrow());
                        ctx.getSource().sendFeedback(() -> Text.literal("Статы сброшены (навыки сохранены)"), false);
                        return 1;
                    }))
                    .then(CommandManager.literal("xp")
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                        int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                        for (Skill sk : Skill.values()) Skills.addXp(p, sk, amount);
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("Опыт добавлен во все навыки: " + amount), false);
                                        return 1;
                                    }))));

            // Команда для всех игроков: /skills
            dispatcher.register(CommandManager.literal("skills").executes(ctx -> {
                ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                PlayerStats s = StatsManager.get(p);
                StringBuilder sb = new StringBuilder("Навыки:");
                for (Skill sk : Skill.values()) {
                    int lvl = s.level[sk.ordinal()];
                    sb.append("\n").append(sk.title).append(": ").append(lvl);
                    if (lvl >= Skills.MAX_LEVEL) sb.append(" (макс.)");
                    else sb.append(" (").append(s.xp[sk.ordinal()]).append("/").append(Skills.need(lvl)).append(" XP)");
                }
                sb.append("\nВыносливость: меньше усталости. Сила: больше вес. ")
                  .append("Скрытность: тише шум. Медицина: бинт лечит.");
                String text = sb.toString();
                ctx.getSource().sendFeedback(() -> Text.literal(text), false);
                return 1;
            }));
        });
    }
}
