package com.phenomenon.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.entity.effect.StatusEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void phenomenon$invertMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.player.hasStatusEffect(StatusEffects.HASTE)) {
            var effect = client.player.getStatusEffect(StatusEffects.HASTE);
            if (effect != null && effect.getAmplifier() == 10) {
                this.movementForward = -this.movementForward;
                this.movementSideways = -this.movementSideways;
            }
        }
    }
}
