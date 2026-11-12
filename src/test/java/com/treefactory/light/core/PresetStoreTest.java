package com.treefactory.light.core;

import com.treefactory.light.core.config.*;
import com.treefactory.light.core.algorithm.BranchMorphology;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PresetStoreTest {
    @TempDir Path directory;
    @Test void jsonRoundTripsUnicodeAndIgnoresBrokenFiles() throws Exception {
        PresetStore store=new PresetStore(directory);
        var saved=store.save("古木橡树_01","玩家",TreeConfig.defaults());
        Files.writeString(directory.resolve("broken.json"),"{not-json}");
        List<String> warnings=new ArrayList<>(); var loaded=store.loadAll(warnings::add);
        assertEquals(List.of(saved),loaded); assertEquals(1,warnings.size());
        assertThrows(IllegalArgumentException.class,()->store.thumbnail("../escape"));
    }
    @Test void unknownVersionsAndNonFiniteNumbersAreRejected() {
        String json=PresetStore.encode("Oak","Player",TreeConfig.defaults());
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("\"1.7\"","\"2.0\"")));
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("0.35","1e999")));
    }
    @Test void versionOnePresetsGainDefaultGeometryAndPalette() {
        String current=PresetStore.encode("Oak","Player",TreeConfig.defaults());
        var root=legacyRoot(current);
        root.addProperty("version","1.0"); root.remove("canopy");
        var palette=root.getAsJsonObject("palette");
        for(String key:List.of("thick_branch","thin_branch","secondary_leaf","accent_block",
                "primary_weight","secondary_weight","accent_weight")) palette.remove(key);
        var loaded=PresetStore.decode("legacy",root.toString()).config();
        assertEquals(TreeConfig.defaults().withCrownBase(.76-.38*1.25),loaded);
    }
    @Test void versionOnePointOnePresetsGainDefaultGravityInfluence() {
        String current=PresetStore.encode("Oak","Player",TreeConfig.defaults().withGravityInfluence(.8));
        var root=legacyRoot(current);
        root.addProperty("version","1.1");
        root.getAsJsonObject("morphology").remove("gravity_influence");
        var loaded=PresetStore.decode("legacy",root.toString()).config();
        assertEquals(TreeConfig.DEFAULT_GRAVITY_INFLUENCE,loaded.gravityInfluence());
    }
    @Test void versionOnePointTwoGravityPreservesOriginalGrowthBias() {
        for (double oldGravity : new double[] {0, .2, .4, .41, .7, 1}) {
            var root=legacyRoot(PresetStore.encode("Oak","Player",TreeConfig.defaults()));
            root.addProperty("version","1.2");
            root.getAsJsonObject("morphology").addProperty("gravity_influence",oldGravity);
            TreeConfig loaded=PresetStore.decode("legacy",root.toString()).config();
            var bias=BranchMorphology.growthBias(loaded);
            assertEquals(.14 - oldGravity * .8,bias.yBias(),1e-12);
            assertEquals(bias.yBias() < 0,bias.allowDownward());
            assertEquals(loaded,PresetStore.decode("new",PresetStore.encode("Oak","Player",loaded)).config());
        }
    }
    @Test void recentlyModifiedPresetsAppearFirst() throws Exception {
        PresetStore store=new PresetStore(directory);
        var older=store.save("Older","Player",TreeConfig.defaults());
        var newer=store.save("Newer","Player",TreeConfig.defaults());
        Files.setLastModifiedTime(directory.resolve(older.id()+".json"),FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(directory.resolve(newer.id()+".json"),FileTime.fromMillis(2_000));
        assertEquals(List.of(newer,older),store.loadAll(warning->fail(warning)));
    }
    @Test void legacyScaleDoesNotOverrideExplicitHeight() {
        var root=legacyRoot(PresetStore.encode("Oak","Player",TreeConfig.defaults().withTrunkHeight(10)));
        root.addProperty("version","1.6");
        root.addProperty("scale","large");
        var loaded=PresetStore.decode("old",root.toString()).config();
        assertEquals(10,loaded.trunkHeight());
        assertEquals(TreeScale.SMALL,loaded.scale());
        assertEquals(TreeConfig.defaults().withTrunkHeight(10),loaded);
    }

    static com.google.gson.JsonObject legacyRoot(String json) {
        var root=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        root.add("preset_type",root.remove("tree_type"));
        root.add("height",root.remove("trunk_height"));
        root.addProperty("scale","medium");
        var structure=root.getAsJsonObject("structure");
        var canopy=root.getAsJsonObject("canopy");
        canopy.add("pipe_exponent",structure.get("pipe_exponent"));
        canopy.add("trunk_thickness",structure.get("trunk_thickness"));
        root.remove("structure");
        return root;
    }
}
