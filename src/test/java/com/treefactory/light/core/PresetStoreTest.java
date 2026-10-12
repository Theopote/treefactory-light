package com.treefactory.light.core;

import com.treefactory.light.core.config.*;
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
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("\"1.1\"","\"2.0\"")));
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("0.35","1e999")));
    }
    @Test void versionOnePresetsGainDefaultGeometryAndPalette() {
        String current=PresetStore.encode("Oak","Player",TreeConfig.defaults());
        var root=com.google.gson.JsonParser.parseString(current).getAsJsonObject();
        root.addProperty("version","1.0"); root.remove("canopy");
        var palette=root.getAsJsonObject("palette");
        for(String key:List.of("thick_branch","thin_branch","secondary_leaf","accent_block",
                "primary_weight","secondary_weight","accent_weight")) palette.remove(key);
        var loaded=PresetStore.decode("legacy",root.toString()).config();
        assertEquals(TreeConfig.defaults(),loaded);
    }
    @Test void recentlyModifiedPresetsAppearFirst() throws Exception {
        PresetStore store=new PresetStore(directory);
        var older=store.save("Older","Player",TreeConfig.defaults());
        var newer=store.save("Newer","Player",TreeConfig.defaults());
        Files.setLastModifiedTime(directory.resolve(older.id()+".json"),FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(directory.resolve(newer.id()+".json"),FileTime.fromMillis(2_000));
        assertEquals(List.of(newer,older),store.loadAll(warning->fail(warning)));
    }
}
