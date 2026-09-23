package com.jigartp.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class JigaRTP extends JavaPlugin {

    private final Map<String, WorldBounds> worldBounds = new HashMap<>();
    private RTPManager rtpManager;

    private int cooldownSeconds;
    private int countdownSeconds;
    private boolean firstJoinRtp;
    private String firstJoinWorld;
    private int maxAttempts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        this.rtpManager = new RTPManager(this);

        getCommand("jigartp").setExecutor(new JigaRTPCommand(this));
        getServer().getPluginManager().registerEvents(new FirstJoinListener(this), this);

        getLogger().info("JigaRTP has been enabled. Loaded " + worldBounds.size() + " RTP-enabled world(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("JigaRTP has been disabled.");
    }

    /**
     * Reloads config.yml and re-parses all settings. Used by /jigartp reload.
     */
    public void reloadJigaConfig() {
        reloadConfig();
        loadConfigValues();
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();

        this.cooldownSeconds = config.getInt("cooldown-seconds", 30);
        this.countdownSeconds = config.getInt("countdown-seconds", 5);
        this.firstJoinRtp = config.getBoolean("first-join-rtp", true);
        this.firstJoinWorld = config.getString("first-join-world", "default-world");
        this.maxAttempts = config.getInt("max-attempts", 30);

        worldBounds.clear();
        if (config.isConfigurationSection("worlds")) {
            for (String worldName : config.getConfigurationSection("worlds").getKeys(false)) {
                String path = "worlds." + worldName + ".";
                boolean enabled = config.getBoolean(path + "enabled", false);
                if (!enabled) {
                    continue;
                }

                double minX = config.getDouble(path + "min-x");
                double maxX = config.getDouble(path + "max-x");
                double minY = config.getDouble(path + "min-y", 60);
                double maxY = config.getDouble(path + "max-y", 200);
                double minZ = config.getDouble(path + "min-z");
                double maxZ = config.getDouble(path + "max-z");

                worldBounds.put(worldName, new WorldBounds(minX, maxX, minY, maxY, minZ, maxZ));
            }
        }
    }

    public Map<String, WorldBounds> getWorldBounds() {
        return worldBounds;
    }

    public RTPManager getRtpManager() {
        return rtpManager;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public boolean isFirstJoinRtp() {
        return firstJoinRtp;
    }

    public String getFirstJoinWorld() {
        return firstJoinWorld;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
