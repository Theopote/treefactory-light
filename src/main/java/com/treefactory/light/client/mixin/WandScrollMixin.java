package com.treefactory.light.client.mixin;

import com.treefactory.light.client.ClientTreeState;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class WandScrollMixin {
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void treefactory$scale(long window,double horizontal,double vertical,CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance();
        if(window==mc.getWindow().handle() && mc.gui.screen()==null && ClientTreeState.INSTANCE.holdingWand()
                && mc.player.isShiftKeyDown() && vertical!=0) {
            ClientTreeState.INSTANCE.cycleScale(vertical>0?1:-1); ci.cancel();
        }
    }
}
