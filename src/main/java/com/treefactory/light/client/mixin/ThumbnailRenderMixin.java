package com.treefactory.light.client.mixin;

import com.treefactory.light.client.gui.render.ThumbnailFBOMaker;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ThumbnailRenderMixin {
    // END_MAIN still owns a PreparedFrame in 26.3. RETURN runs after that frame is closed.
    @Inject(method="render",at=@At("RETURN"))
    private void treefactory$thumbnail(CallbackInfo ci) {
        ThumbnailFBOMaker.renderPending();
    }
}
