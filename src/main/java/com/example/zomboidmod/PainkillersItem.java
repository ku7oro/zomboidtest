package com.example.zomboidmod;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PainkillersItem extends Item {
    public PainkillersItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            user.removeStatusEffect(StatusEffects.NAUSEA);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1800, 1));
            stack.decrementUnlessCreative(1, user);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }
}
