package com.treefactory.light.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import com.treefactory.light.TreefactoryLight;
import com.treefactory.light.client.gui.render.*;
import com.treefactory.light.network.S2CTreeCommitResultPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class TreefactoryLightClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(TreefactoryLight.id("controls"));
	private static final KeyMapping UNDO=KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.treefactory-light.undo", InputConstants.KEY_Z, CATEGORY));
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientTreeState.INSTANCE.tick();
			while (UNDO.consumeClick()) ClientTreeState.INSTANCE.undo();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> client.execute(ClientTreeState.INSTANCE::disconnect));
		ClientPlayNetworking.registerGlobalReceiver(S2CTreeCommitResultPacket.TYPE,(packet, context) ->
				context.client().execute(() -> ClientTreeState.INSTANCE.onCommitResult(packet.success())));
		PictureInPictureRendererRegistry.register(context -> new TreePictureRenderer());
	}
}
