package ropeladders.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Path;

public class RopeLaddersConfig {
    public static int maxChainLength = 512;
    public static boolean particlesEnabled = true;
    public static boolean soundsEnabled = true;
    public static double raycastDistance = 3.0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ropeladders.json");

    public static void load() {
        if (!CONFIG_PATH.toFile().exists()) {
            saveDefault();
            return;
        }
        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            maxChainLength = data.maxChainLength;
            particlesEnabled = data.particlesEnabled;
            soundsEnabled = data.soundsEnabled;
            raycastDistance = data.raycastDistance;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveDefault() {
        ConfigData data = new ConfigData();
        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ConfigData {
        int maxChainLength = 512;
        boolean particlesEnabled = true;
        boolean soundsEnabled = true;
        double raycastDistance = 3.0;
    }
}