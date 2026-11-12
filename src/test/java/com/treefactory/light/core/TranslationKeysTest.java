package com.treefactory.light.core;

import com.google.gson.JsonParser;
import com.treefactory.light.core.config.BuiltInPresets;
import com.treefactory.light.core.config.CanopyShape;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationKeysTest {
    @Test void languagesContainTheSameKeysAndDynamicNames() {
        var english=load("en_us");
        var chinese=load("zh_cn");
        assertEquals(english.keySet(),chinese.keySet());
        for (var shape : CanopyShape.values())
            assertTrue(english.has("treefactory.canopy."+shape.name().toLowerCase(Locale.ROOT)),shape.name());
        for (var preset : BuiltInPresets.all())
            assertTrue(english.has("treefactory.builtin."+preset.id().substring(BuiltInPresets.PREFIX.length())),preset.id());
        for (String key : Set.of("generator","library","finish_tree","choose_materials","new_seed","save_preset",
                "trunk_thickness","crookedness","canopy_size","spread","fullness","crown_stretch",
                "crown_layers","attraction_density","taper","gravity_influence","secondary_weight","accent_weight"))
            assertTrue(english.has("treefactory."+key+".tip"),key);
    }

    private static com.google.gson.JsonObject load(String locale) {
        String path="/assets/treefactory-light/lang/"+locale+".json";
        try (var stream=Objects.requireNonNull(TranslationKeysTest.class.getResourceAsStream(path),path);
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }
}
