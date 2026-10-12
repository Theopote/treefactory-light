package com.treefactory.light.client.mixin;

import com.treefactory.light.client.gui.DrawerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudCrosshairMixin {
    @Inject(method="extractCrosshair",at=@At("HEAD"),cancellable=true)
    private void treefactory$hideCrosshair(CallbackInfo ci) {
        if(Minecraft.getInstance().gui.screen() instanceof DrawerScreen) ci.cancel();
    }
}
