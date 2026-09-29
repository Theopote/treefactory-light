package com.treefactory.light.core.config;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned preset JSON; file names never come from user-supplied display names. */
public final class PresetStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;
    public record Preset(String id, String name, String author, TreeConfig config) {}
    public PresetStore(Path directory) { this.directory = directory; }
    public Path thumbnail(String id) {
        if (!id.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid preset id");
        return directory.resolve(id + ".png");
    }
    public Preset save(String name, String author, TreeConfig config) throws IOException {
        if (name == null || name.isBlank() || name.length()>64) throw new IllegalArgumentException("Name must contain 1–64 characters");
        Files.createDirectories(directory);
        String id = UUID.randomUUID().toString();
        Path temporary = Files.createTempFile(directory,"preset-",".tmp");
        try {
            Files.writeString(temporary,encode(name,author,config),StandardCharsets.UTF_8);
            try { Files.move(temporary,directory.resolve(id+".json"),StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary,directory.resolve(id+".json")); }
        } finally { Files.deleteIfExists(temporary); }
        return new Preset(id,name,author,config);
    }
    public List<Preset> loadAll(java.util.function.Consumer<String> warning) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Preset> presets = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().limit(256).toList()) {
                try {
                    if (Files.size(path)>16384) throw new IllegalArgumentException("File too large");
                    String file = path.getFileName().toString();
                    String id = file.substring(0,file.length()-5);
                    thumbnail(id);
                    presets.add(decode(id,Files.readString(path,StandardCharsets.UTF_8)));
                } catch (RuntimeException | IOException e) { warning.accept(path.getFileName()+": "+e.getMessage()); }
            }
        }
        return List.copyOf(presets);
    }
    public static String encode(String name, String author, TreeConfig c) {
        JsonObject root = new JsonObject();
        root.addProperty("name",name); root.addProperty("author",author); root.addProperty("version","1.0");
        root.addProperty("preset_type",c.preset().id()); root.addProperty("scale",c.scale().id()); root.addProperty("seed",c.seed());
        JsonObject dna = new JsonObject();
        dna.addProperty("crookedness",c.crookedness()); dna.addProperty("spread",c.spread()); dna.addProperty("fullness",c.fullness());
        root.add("dna",dna);
        JsonObject palette = new JsonObject();
        palette.addProperty("log_block",c.logBlock()); palette.addProperty("leaves_block",c.leavesBlock()); root.add("palette",palette);
        return GSON.toJson(root);
    }
    public static Preset decode(String id, String json) {
        if (json.length()>16384) throw new IllegalArgumentException("Preset too large");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!"1.0".equals(root.get("version").getAsString())) throw new IllegalArgumentException("Unsupported preset version");
        String name = root.get("name").getAsString(), author = root.get("author").getAsString();
        if (name.isBlank() || name.length()>64 || author.length()>64) throw new IllegalArgumentException("Invalid preset metadata");
        JsonObject dna = root.getAsJsonObject("dna"), palette = root.getAsJsonObject("palette");
        TreeConfig config = new TreeConfig(TreePreset.valueOf(root.get("preset_type").getAsString().toUpperCase(Locale.ROOT)),
                TreeScale.valueOf(root.get("scale").getAsString().toUpperCase(Locale.ROOT)),root.get("seed").getAsLong(),
                dna.get("crookedness").getAsDouble(),dna.get("spread").getAsDouble(),dna.get("fullness").getAsDouble(),
                palette.get("log_block").getAsString(),palette.get("leaves_block").getAsString());
        return new Preset(id,name,author,config);
    }
}
