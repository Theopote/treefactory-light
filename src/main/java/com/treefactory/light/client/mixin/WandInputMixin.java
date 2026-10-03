package com.treefactory.light.client.mixin;

import com.treefactory.light.client.ClientTreeState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public abstract class WandInputMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void treefactory$use(CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance(); ClientTreeState state=ClientTreeState.INSTANCE;
        if(!state.holdingWand()) return;
        if(mc.player.isShiftKeyDown()) state.open(); else state.build();
        ci.cancel();
    }
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void treefactory$undo(CallbackInfoReturnable<Boolean> ci) {
        Minecraft mc=Minecraft.getInstance();
        if(ClientTreeState.INSTANCE.holdingWand()) {
            if(mc.player.isShiftKeyDown()) ClientTreeState.INSTANCE.undo();
            ci.setReturnValue(false);
        }
    }
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void treefactory$preventMining(boolean attacking,CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance();
        if(ClientTreeState.INSTANCE.holdingWand()) ci.cancel();
    }
}
