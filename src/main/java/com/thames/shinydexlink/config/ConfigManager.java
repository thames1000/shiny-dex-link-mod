package com.thames.shinydexlink.config;

import com.thames.shinydexlink.util.JsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private final Path configPath;
    private ShinyDexConfig config;

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    public ShinyDexConfig load() throws IOException {
        if (Files.notExists(configPath)) {
            config = new ShinyDexConfig();
            save();
            return config;
        }

        config = JsonUtil.read(configPath, ShinyDexConfig.class);
        if (config == null) {
            config = new ShinyDexConfig();
        }
        config.normalize();
        save();
        return config;
    }

    public void save() throws IOException {
        if (config == null) {
            config = new ShinyDexConfig();
        }
        config.normalize();
        JsonUtil.writeAtomic(configPath, config);
    }

    public ShinyDexConfig config() {
        return config;
    }

    public Path configPath() {
        return configPath;
    }
}
