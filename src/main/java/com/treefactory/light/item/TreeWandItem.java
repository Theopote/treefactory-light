package com.treefactory.light.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class TreeWandItem extends Item {
    public TreeWandItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) { return InteractionResult.SUCCESS; }
    @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.SUCCESS; }
}
