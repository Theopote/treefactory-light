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
        return directory.resolve(id + "-v6.png");
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
    public void delete(String id) throws IOException {
        Path thumbnail=thumbnail(id);
        Files.deleteIfExists(directory.resolve(id+".json"));
        Files.deleteIfExists(thumbnail);
    }
    public List<Preset> loadAll(java.util.function.Consumer<String> warning) throws IOException {
        if (!Files.isDirectory(directory)) return List.of();
        List<Preset> presets = new ArrayList<>();
        try (var files = Files.list(directory)) {
            List<Path> paths=new ArrayList<>(files.filter(p -> p.getFileName().toString().endsWith(".json")).toList());
            paths.sort(Comparator.comparingLong(PresetStore::modified).reversed());
            if(paths.size()>256) warning.accept("Showing the 256 most recently modified presets of "+paths.size());
            for (Path path : paths.stream().limit(256).toList()) {
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
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch(IOException e) { return Long.MIN_VALUE; }
    }
    public static String encode(String name, String author, TreeConfig c) {
        JsonObject root = new JsonObject();
        root.addProperty("name",name); root.addProperty("author",author); root.addProperty("version","1.8");
        root.addProperty("tree_type",c.treeType().id()); root.addProperty("trunk_height",c.trunkHeight()); root.addProperty("seed",c.seed());
        JsonObject dna = new JsonObject();
        dna.addProperty("crookedness",c.crookedness()); dna.addProperty("spread",c.spread()); dna.addProperty("fullness",c.fullness());
        root.add("dna",dna);
        JsonObject canopy=new JsonObject();
        canopy.addProperty("shape",c.canopyShape().name().toLowerCase(Locale.ROOT));
        canopy.addProperty("scale",c.canopyScale()); canopy.addProperty("density",c.attractionDensity());
        canopy.addProperty("crown_stretch",c.crownStretch()); canopy.addProperty("crown_layers",c.crownLayers());
        canopy.addProperty("crown_base",c.crownBase()); root.add("canopy",canopy);
        JsonObject structure=new JsonObject();
        structure.addProperty("taper",c.taper()); structure.addProperty("trunk_thickness",c.trunkThickness());
        root.add("structure",structure);
        JsonObject morphology=new JsonObject();
        morphology.addProperty("branch_angle_bias",c.branchAngleBias());
        morphology.addProperty("branch_droop",c.branchDroop());
        morphology.addProperty("apical_dominance",c.apicalDominance());
        morphology.addProperty("gravity_influence",c.gravityInfluence());
        root.add("morphology",morphology);
        JsonObject palette = new JsonObject();
        BlockPalette p=c.blockPalette();
        palette.addProperty("log_block",p.mainTrunk()); palette.addProperty("leaves_block",p.primaryLeaf());
        palette.addProperty("thick_branch",p.thickBranch()); palette.addProperty("thin_branch",p.thinBranch());
        palette.addProperty("secondary_leaf",p.secondaryLeaf()); palette.addProperty("accent_block",p.accentBlock());
        palette.addProperty("primary_weight",p.primaryWeight()); palette.addProperty("secondary_weight",p.secondaryWeight());
        palette.addProperty("accent_weight",p.accentWeight()); root.add("palette",palette);
        return GSON.toJson(root);
    }
    public static Preset decode(String id, String json) {
        if (json.length()>16384) throw new IllegalArgumentException("Preset too large");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String version=root.get("version").getAsString();
        if (!"1.8".equals(version)) throw new IllegalArgumentException("Unsupported preset version");
        String name = root.get("name").getAsString(), author = root.get("author").getAsString();
        if (name.isBlank() || name.length()>64 || author.length()>64) throw new IllegalArgumentException("Invalid preset metadata");
        JsonObject dna = root.getAsJsonObject("dna"), palette = root.getAsJsonObject("palette");
        TreeType type=TreeType.valueOf(root.get("tree_type").getAsString().toUpperCase(Locale.ROOT));
        JsonObject canopy=root.getAsJsonObject("canopy");
        BlockPalette blocks=new BlockPalette(palette.get("log_block").getAsString(),palette.get("thick_branch").getAsString(),
                palette.get("thin_branch").getAsString(),palette.get("leaves_block").getAsString(),palette.get("secondary_leaf").getAsString(),
                palette.get("accent_block").getAsString(),palette.get("primary_weight").getAsDouble(),
                palette.get("secondary_weight").getAsDouble(),palette.get("accent_weight").getAsDouble());
        JsonObject structure=root.getAsJsonObject("structure"), morphology=root.getAsJsonObject("morphology");
        TreeConfig config=new TreeConfig(type,root.get("seed").getAsLong(),dna.get("crookedness").getAsDouble(),
                dna.get("spread").getAsDouble(),dna.get("fullness").getAsDouble(),
                CanopyShape.valueOf(canopy.get("shape").getAsString().toUpperCase(Locale.ROOT)),canopy.get("scale").getAsDouble(),
                canopy.get("density").getAsDouble(),structure.get("taper").getAsDouble(),blocks,root.get("trunk_height").getAsDouble(),
                structure.get("trunk_thickness").getAsDouble(),canopy.get("crown_stretch").getAsDouble(),
                canopy.get("crown_layers").getAsInt(),morphology.get("branch_angle_bias").getAsDouble(),
                morphology.get("branch_droop").getAsDouble(),morphology.get("apical_dominance").getAsDouble(),
                morphology.get("gravity_influence").getAsDouble(),canopy.get("crown_base").getAsDouble());
        return new Preset(id,name,author,config);
    }
}
