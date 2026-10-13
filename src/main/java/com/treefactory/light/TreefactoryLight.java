package com.treefactory.light;

import net.fabricmc.api.ModInitializer;
import com.treefactory.light.item.TreeWandItem;
import com.treefactory.light.network.*;
import com.treefactory.light.util.WorldBatchPaster;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TreefactoryLight implements ModInitializer {
	public static final String MOD_ID = "treefactory-light";
	public static final Item TREE_WAND = new TreeWandItem(new Item.Properties()
			.setId(ResourceKey.create(Registries.ITEM,id("tree_wand"))).stacksTo(1));
	private static final WorldBatchPaster PASTER = new WorldBatchPaster();

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ITEM,id("tree_wand"),TREE_WAND);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(TREE_WAND));
		PayloadTypeRegistry.serverboundPlay().register(C2SPreviewTreePacket.TYPE,C2SPreviewTreePacket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(C2SFinishTreePacket.TYPE,C2SFinishTreePacket.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(C2SUndoTreePacket.TYPE,C2SUndoTreePacket.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(C2SPreviewTreePacket.TYPE,(packet,context) ->
				context.server().execute(() -> PASTER.preview(context.player(),packet.origin(),packet.config())));
		ServerPlayNetworking.registerGlobalReceiver(C2SFinishTreePacket.TYPE,(packet,context) ->
				context.server().execute(() -> {
					if (packet.commit()) PASTER.commit(context.player()); else PASTER.discard(context.player().getUUID());
				}));
		ServerPlayNetworking.registerGlobalReceiver(C2SUndoTreePacket.TYPE,(packet,context) ->
				context.server().execute(() -> PASTER.undo(context.player())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> PASTER.disconnect(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> PASTER.attach(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PASTER.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> PASTER.tick());
		ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated) -> PASTER.chunkLoaded(level, chunk.getPos()));
		LOGGER.info("Treefactory Light beta initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
