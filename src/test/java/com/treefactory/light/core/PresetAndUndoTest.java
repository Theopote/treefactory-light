package com.treefactory.light.core;

import com.treefactory.light.core.config.*;
import com.treefactory.light.util.UndoHistory;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PresetAndUndoTest {
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
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("\"1.0\"","\"2.0\"")));
        assertThrows(IllegalArgumentException.class,()->PresetStore.decode("id",json.replace("0.35","1e999")));
    }
    @Test void historyIsBoundedLifoAndIsolatedByPlayer() {
        UndoHistory<Integer> history=new UndoHistory<>(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        for(int i=0;i<8;i++) history.push(a,i);
        history.push(b,99);
        for(int i=7;i>=3;i--) assertEquals(Integer.valueOf(i),history.pop(a));
        assertNull(history.pop(a)); assertEquals(Integer.valueOf(99),history.pop(b));
        history.push(a,1); history.remove(a); assertNull(history.peek(a));
    }
}
