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
        return directory.resolve(id + "-v2.png");
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
        root.addProperty("name",name); root.addProperty("author",author); root.addProperty("version","1.1");
        root.addProperty("preset_type",c.preset().id()); root.addProperty("scale",c.scale().id()); root.addProperty("height",c.height()); root.addProperty("seed",c.seed());
        JsonObject dna = new JsonObject();
        dna.addProperty("crookedness",c.crookedness()); dna.addProperty("spread",c.spread()); dna.addProperty("fullness",c.fullness());
        root.add("dna",dna);
        JsonObject canopy=new JsonObject();
        canopy.addProperty("shape",c.canopyShape().name().toLowerCase(Locale.ROOT));
        canopy.addProperty("scale",c.canopyScale()); canopy.addProperty("density",c.attractionDensity());
        canopy.addProperty("pipe_exponent",c.pipeExponent()); canopy.addProperty("trunk_thickness",c.trunkThickness());
        canopy.addProperty("crown_stretch",c.crownStretch()); canopy.addProperty("crown_layers",c.crownLayers()); root.add("canopy",canopy);
        JsonObject morphology=new JsonObject();
        morphology.addProperty("branch_angle_bias",c.branchAngleBias());
        morphology.addProperty("branch_droop",c.branchDroop());
        morphology.addProperty("apical_dominance",c.apicalDominance());
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
        if (!"1.0".equals(version) && !"1.1".equals(version)) throw new IllegalArgumentException("Unsupported preset version");
        String name = root.get("name").getAsString(), author = root.get("author").getAsString();
        if (name.isBlank() || name.length()>64 || author.length()>64) throw new IllegalArgumentException("Invalid preset metadata");
        JsonObject dna = root.getAsJsonObject("dna"), palette = root.getAsJsonObject("palette");
        TreePreset type=TreePreset.valueOf(root.get("preset_type").getAsString().toUpperCase(Locale.ROOT));
        TreeConfig legacy = new TreeConfig(type,
                TreeScale.valueOf(root.get("scale").getAsString().toUpperCase(Locale.ROOT)),root.get("seed").getAsLong(),
                dna.get("crookedness").getAsDouble(),dna.get("spread").getAsDouble(),dna.get("fullness").getAsDouble(),
                palette.get("log_block").getAsString(),palette.get("leaves_block").getAsString());
        if("1.0".equals(version)) return new Preset(id,name,author,legacy);
        JsonObject canopy=root.getAsJsonObject("canopy");
        BlockPalette blocks=new BlockPalette(legacy.logBlock(),palette.get("thick_branch").getAsString(),
                palette.get("thin_branch").getAsString(),legacy.leavesBlock(),palette.get("secondary_leaf").getAsString(),
                palette.get("accent_block").getAsString(),palette.get("primary_weight").getAsDouble(),
                palette.get("secondary_weight").getAsDouble(),palette.get("accent_weight").getAsDouble());
        double height=root.has("height")?root.get("height").getAsDouble():legacy.height();
        JsonObject morphology=root.has("morphology")?root.getAsJsonObject("morphology"):null;
        double angleBias=morphology!=null && morphology.has("branch_angle_bias")?morphology.get("branch_angle_bias").getAsDouble():TreeConfig.DEFAULT_BRANCH_ANGLE_BIAS;
        double droop=morphology!=null && morphology.has("branch_droop")?morphology.get("branch_droop").getAsDouble():TreeConfig.DEFAULT_BRANCH_DROOP;
        double apical=morphology!=null && morphology.has("apical_dominance")?morphology.get("apical_dominance").getAsDouble():TreeConfig.DEFAULT_APICAL_DOMINANCE;
        TreeConfig config=new TreeConfig(type,legacy.scale(),legacy.seed(),legacy.crookedness(),legacy.spread(),legacy.fullness(),
                CanopyShape.valueOf(canopy.get("shape").getAsString().toUpperCase(Locale.ROOT)),canopy.get("scale").getAsDouble(),
                canopy.get("density").getAsDouble(),canopy.get("pipe_exponent").getAsDouble(),blocks,height,
                canopy.has("trunk_thickness")?canopy.get("trunk_thickness").getAsDouble():1.0,
                canopy.has("crown_stretch")?canopy.get("crown_stretch").getAsDouble():1.0,
                canopy.has("crown_layers")?canopy.get("crown_layers").getAsInt():3,angleBias,droop,apical);
        return new Preset(id,name,author,config);
    }
}
