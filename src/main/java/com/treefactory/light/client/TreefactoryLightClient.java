package com.treefactory.light.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import com.treefactory.light.client.gui.render.*;

public class TreefactoryLightClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientTreeState.INSTANCE.tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> ClientTreeState.INSTANCE.disconnect());
		PictureInPictureRendererRegistry.register(context -> new TreePictureRenderer());
		HologramRenderer.register();
	}
}
