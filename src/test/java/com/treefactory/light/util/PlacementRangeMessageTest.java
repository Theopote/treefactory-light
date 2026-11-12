package com.treefactory.light.util;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacementRangeMessageTest {
    @Test void localizedMessagesDisplayThePlacementLimit() throws Exception {
        assertEquals(512, WorldBatchPaster.PLACE_RANGE);
        for (String language : new String[] {"en_us", "zh_cn"}) {
            String path = "/assets/treefactory-light/lang/" + language + ".json";
            try (var reader = new InputStreamReader(
                    Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
                String template = JsonParser.parseReader(reader).getAsJsonObject()
                        .get("treefactory.target_too_far").getAsString();
                assertTrue(String.format(template, WorldBatchPaster.PLACE_RANGE).contains("512"), language);
            }
        }
    }
}
