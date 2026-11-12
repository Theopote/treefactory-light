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
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("\"1.8\"","\"2.0\"")));
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("0.35","1e999")));
    }
    @Test void olderVersionsAndMissingCurrentFieldsAreRejected() {
        String current=PresetStore.encode("Oak","Player",TreeConfig.defaults());
        for (String version : List.of("1.0","1.1","1.2","1.3","1.4","1.5","1.6","1.7"))
            assertThrows(IllegalArgumentException.class, () -> PresetStore.decode("id",current.replace("\"1.8\"","\""+version+"\"")));
        var root=com.google.gson.JsonParser.parseString(current).getAsJsonObject();
        root.getAsJsonObject("morphology").remove("gravity_influence");
        assertThrows(RuntimeException.class, () -> PresetStore.decode("id",root.toString()));
    }
    @Test void taperUsesCurrentStructureFieldAndRoundTrips() {
        var config=TreeConfig.defaults().withTaper(.73);
        String json=PresetStore.encode("Oak","Player",config);
        var structure=com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("structure");
        assertEquals(.73,structure.get("taper").getAsDouble());
        assertFalse(structure.has("pipe_exponent"));
        assertEquals(config,PresetStore.decode("id",json).config());
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
