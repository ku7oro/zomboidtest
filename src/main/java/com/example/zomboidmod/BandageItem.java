package com.example.zomboidmod;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BandageItem extends Item {
    public BandageItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity p) {
            PlayerStats s = StatsManager.get(p);
            if (!s.bleeding) return TypedActionResult.fail(stack);
            s.bleeding = false;
            Skills.addXp(p, Skill.FIRST_AID, 25);
            p.heal(1.0f + Skills.level(p, Skill.FIRST_AID) / 2);
            stack.decrementUnlessCreative(1, user);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }
}
