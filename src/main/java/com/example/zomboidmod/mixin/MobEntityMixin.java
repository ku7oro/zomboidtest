package com.example.zomboidmod.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
    private void zomboid$noDaylightBurn(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ZombieEntity) {
            cir.setReturnValue(false);
        }
    }
}
