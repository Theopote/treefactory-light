package com.treefactory.light.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Presets shipped with the mod. A preset's id is also its thumbnail cache name, so changing the config
 * of an existing id would leave a stale thumbnail on disk: give a changed tree a new id instead.
 */
public final class BuiltInPresets {
    public static final String PREFIX = "builtin-";
    private static final List<PresetStore.Preset> ALL = create();
    private BuiltInPresets() {}

    public static List<PresetStore.Preset> all() { return ALL; }

    private static List<PresetStore.Preset> create() {
        List<PresetStore.Preset> list = new ArrayList<>();
        for (TreeType type : TreeType.values())
            list.add(preset(type.id(), TreeConfig.defaults().withTreeType(type)));
        list.add(preset("birch", tree(TreeType.OAK, 18, 2101L, .2, .45, .7,
                CanopyShape.OVOID, 1.0, 1.1, 2.8, palette("birch", "oak", .96, .04, 0), .55, 0, .55)));
        list.add(preset("cherry", tree(TreeType.OAK, 14, 2102L, .55, .8, .85,
                CanopyShape.OBLATE_ELLIPSOID, 1.35, 1.3, 2.75, palette("cherry", "flowering_azalea", .88, .1, .02),
                .78, .08, .32)));
        list.add(preset("acacia", tree(TreeType.OAK, 14, 2103L, .75, .85, .6,
                CanopyShape.UMBRELLA, 1.4, 1.0, 2.7, palette("acacia", "oak", .97, .03, 0), .88, .12, .25)));
        list.add(preset("dark_oak", tree(TreeType.OAK, 22, 2104L, .4, .75, .9,
                CanopyShape.OBOVATE, 1.4, 1.4, 2.9, palette("dark_oak", "oak", .97, .03, 0), .45, 0, .42)));
        list.add(preset("jungle", tree(TreeType.OAK, 36, 2105L, .3, .7, .85,
                CanopyShape.SPINDLE_MULTI_LAYER, 1.6, 1.2, 2.6, palette("jungle", "oak", .94, .04, .02), .52, 0, .38)));
        list.add(preset("cypress", tree(TreeType.OAK, 20, 2106L, .15, .25, .85,
                CanopyShape.PROLATE_ELLIPSOID, .9, 1.2, 2.8, palette("spruce", "oak", .98, .02, 0), .1, .04, .92)));
        list.add(preset("mangrove", tree(TreeType.OAK, 15, 2107L, .65, .8, .8,
                CanopyShape.HEMISPHERE, 1.3, 1.1, 2.6, palette("mangrove", "oak", .96, .04, 0), .55, .82, .38)));
        list.add(preset("snag", tree(TreeType.DEAD, 20, 2108L, .8, .7, .5,
                CanopyShape.HEMISPHERE, 1.0, 1.0, 2.9, palette("pale_oak", "oak", 1, 0, 0), .4, 0, .7)));
        return List.copyOf(list);
    }

    private static PresetStore.Preset preset(String id, TreeConfig config) {
        return new PresetStore.Preset(PREFIX + id, id, "Treefactory Light", config);
    }

    private static TreeConfig tree(TreeType type, double height, long seed,
                                   double crookedness, double spread, double fullness, CanopyShape shape,
                                   double canopyScale, double density, double pipe, BlockPalette palette,
                                   double branchAngleBias, double branchDroop, double apicalDominance) {
        return new TreeConfig(type, seed, crookedness, spread, fullness, shape, canopyScale, density, pipe,
                palette, height, 1.0, 1.0, 3, branchAngleBias, branchDroop, apicalDominance,
                TreeConfig.DEFAULT_GRAVITY_INFLUENCE,TreeConfig.DEFAULT_CROWN_BASE);
    }

    private static BlockPalette palette(String wood, String secondaryLeaf, double primary, double secondary, double accent) {
        String log = "minecraft:" + wood + "_log";
        return new BlockPalette(log, log, log, "minecraft:" + wood + "_leaves",
                "minecraft:" + secondaryLeaf + "_leaves", "minecraft:moss_block", primary, secondary, accent);
    }
}
