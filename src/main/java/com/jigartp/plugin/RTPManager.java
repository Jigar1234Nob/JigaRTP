package com.jigartp.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core logic for JigaRTP: finding a safe random location within a world's configured bounds,
 * teleporting the player, and tracking per-player cooldowns.
 */
public class RTPManager {

    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.AQUA + "JigaRTP" + ChatColor.GRAY + "] " + ChatColor.RESET;

    private final JigaRTP plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Set<UUID> counting = new HashSet<>();

    public RTPManager(JigaRTP plugin) {
        this.plugin = plugin;
    }

    public boolean isCountingDown(Player player) {
        return counting.contains(player.getUniqueId());
    }

    /**
     * Sends "[JigaRTP] Teleporting in N" chat messages once a second, then performs the actual
     * RTP once the countdown reaches zero. Cooldown is recorded at the moment the countdown
     * finishes (successful teleport), not when it starts.
     */
    public void startCountdown(Player player, World world) {
        int seconds = plugin.getCountdownSeconds();

        if (seconds <= 0) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Teleporting...");
            finishTeleport(player, world);
            return;
        }

        counting.add(player.getUniqueId());

        new BukkitRunnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    counting.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (remaining <= 0) {
                    counting.remove(player.getUniqueId());
                    finishTeleport(player, world);
                    cancel();
                    return;
                }

                player.sendMessage(PREFIX + ChatColor.YELLOW + "Teleporting in " + remaining);
                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void finishTeleport(Player player, World world) {
        boolean success = attemptRTP(player, world);
        if (success) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "You have been randomly teleported!");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not find a safe location. Please try again.");
        }
    }

    /**
     * Admin-triggered instant RTP that bypasses the player's cooldown entirely (used by
     * /jigartp &lt;player&gt;). Still records a fresh cooldown afterward so the target can't
     * immediately chain a self-RTP on top of it.
     */
    public boolean forceRTP(Player target, World world) {
        return attemptRTP(target, world);
    }

    public boolean isWorldEnabled(World world) {
        return plugin.getWorldBounds().containsKey(world.getName());
    }

    /**
     * Returns remaining cooldown in seconds, or 0 if the player is free to RTP right now.
     */
    public long getRemainingCooldown(Player player) {
        if (player.hasPermission("jigartp.bypass.cooldown")) {
            return 0;
        }
        Long last = lastUse.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        long remaining = plugin.getCooldownSeconds() - elapsed;
        return Math.max(0, remaining);
    }

    public void recordUse(Player player) {
        lastUse.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Attempts to find a safe location and teleport the player instantly.
     * Returns true if successful, false if no safe location could be found in time.
     */
    public boolean attemptRTP(Player player, World world) {
        WorldBounds bounds = plugin.getWorldBounds().get(world.getName());
        if (bounds == null) {
            return false;
        }

        Location safeLocation = findSafeLocation(world, bounds);
        if (safeLocation == null) {
            return false;
        }

        player.teleport(safeLocation);
        recordUse(player);
        applyPostTeleportEffects(player, safeLocation);
        return true;
    }

    /**
     * Gives the player 2 seconds of blindness and shows their new coordinates as an on-screen
     * title (in orange) for 4 seconds, right after a successful RTP.
     */
    private void applyPostTeleportEffects(Player player, Location location) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));

        String coords = String.format("X: %d  Y: %d  Z: %d",
                location.getBlockX(), location.getBlockY(), location.getBlockZ());

        Title title = Title.title(
                Component.text(coords, NamedTextColor.GOLD),
                Component.empty(),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(500))
        );
        player.showTitle(title);
    }

    private Location findSafeLocation(World world, WorldBounds bounds) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < plugin.getMaxAttempts(); attempt++) {
            double x = random.nextDouble(bounds.getMinX(), bounds.getMaxX());
            double z = random.nextDouble(bounds.getMinZ(), bounds.getMaxZ());

            int highestY = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z));

            // Clamp the found ground height into the configured Y range.
            if (highestY < bounds.getMinY() || highestY > bounds.getMaxY()) {
                continue;
            }

            Location candidate = new Location(world, x, highestY + 1.0, z);
            if (isSafe(candidate)) {
                candidate.setX(Math.floor(x) + 0.5);
                candidate.setZ(Math.floor(z) + 0.5);
                return candidate;
            }
        }

        return null;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        Material ground = world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ()).getType();
        Material feet = world.getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()).getType();
        Material head = world.getBlockAt(location.getBlockX(), location.getBlockY() + 1, location.getBlockZ()).getType();

        if (!ground.isSolid()) {
            return false;
        }
        if (ground == Material.LAVA || ground == Material.WATER) {
            return false;
        }
        if (isDangerous(ground)) {
            return false;
        }

        return feet.isAir() && head.isAir();
    }

    private boolean isDangerous(Material material) {
        switch (material) {
            case LAVA:
            case FIRE:
            case CACTUS:
            case MAGMA_BLOCK:
                return true;
            default:
                return false;
        }
    }
}
