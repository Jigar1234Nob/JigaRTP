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
<<<<<<< HEAD
import java.util.function.Consumer;
=======
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0

/**
 * Core logic for JigaRTP: finding a safe random location within a world's configured bounds,
 * teleporting the player, and tracking per-player cooldowns.
<<<<<<< HEAD
 *
 * IMPORTANT (TPS): candidate chunks are pre-loaded/generated via World#getChunkAtAsync before
 * anything reads blocks from them. Doing this synchronously (the naive approach) forces the
 * server to generate/load a chunk on the main thread the instant you call getHighestBlockYAt or
 * getBlockAt on it, which is exactly what causes RTP plugins to spike/lag the whole server -
 * especially out near the edge of explored terrain where chunks aren't generated yet. Async
 * pre-loading moves that work off the main thread so the tick loop never stalls.
=======
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0
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
<<<<<<< HEAD
        attemptRTP(player, world, success -> {
            if (!player.isOnline()) {
                return;
            }
            if (success) {
                player.sendMessage(PREFIX + ChatColor.GREEN + "You have been randomly teleported!");
            } else {
                player.sendMessage(PREFIX + ChatColor.RED + "Could not find a safe location. Please try again.");
            }
        });
=======
        boolean success = attemptRTP(player, world);
        if (success) {
            player.sendMessage(PREFIX + ChatColor.GREEN + "You have been randomly teleported!");
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "Could not find a safe location. Please try again.");
        }
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0
    }

    /**
     * Admin-triggered instant RTP that bypasses the player's cooldown entirely (used by
     * /jigartp &lt;player&gt;). Still records a fresh cooldown afterward so the target can't
     * immediately chain a self-RTP on top of it.
     */
<<<<<<< HEAD
    public void forceRTP(Player target, World world, Consumer<Boolean> callback) {
        attemptRTP(target, world, callback);
=======
    public boolean forceRTP(Player target, World world) {
        return attemptRTP(target, world);
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0
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
<<<<<<< HEAD
     * Asynchronously finds a safe location and teleports the player. The callback fires on the
     * main thread with true on success, false if no safe location could be found within
     * max-attempts.
     */
    public void attemptRTP(Player player, World world, Consumer<Boolean> callback) {
        WorldBounds bounds = plugin.getWorldBounds().get(world.getName());
        if (bounds == null) {
            callback.accept(false);
            return;
        }

        findSafeLocation(world, bounds, plugin.getMaxAttempts(), location -> {
            if (location == null) {
                callback.accept(false);
                return;
            }
            if (!player.isOnline()) {
                callback.accept(false);
                return;
            }

            player.teleport(location);
            recordUse(player);
            applyPostTeleportEffects(player, location);
            callback.accept(true);
        });
=======
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
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0
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

<<<<<<< HEAD
    /**
     * Recursively tries random X/Z coordinates within bounds. For each candidate, the chunk is
     * pre-loaded/generated asynchronously before any block data is read from it, so chunk
     * generation never blocks the main thread. The callback fires on the main thread with a
     * safe Location, or null if attemptsLeft ran out.
     */
    private void findSafeLocation(World world, WorldBounds bounds, int attemptsLeft, Consumer<Location> callback) {
        if (attemptsLeft <= 0) {
            callback.accept(null);
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = random.nextDouble(bounds.getMinX(), bounds.getMaxX());
        double z = random.nextDouble(bounds.getMinZ(), bounds.getMaxZ());

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;

        // gen=true so unexplored terrain is generated off-thread instead of stalling the main
        // thread the moment we touch it. Paper completes this future back on the main thread.
        world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
            int highestY = world.getHighestBlockYAt(blockX, blockZ);

            if (highestY < bounds.getMinY() || highestY > bounds.getMaxY()) {
                findSafeLocation(world, bounds, attemptsLeft - 1, callback);
                return;
            }

            Location candidate = new Location(world, blockX + 0.5, highestY + 1.0, blockZ + 0.5);
            if (isSafe(candidate)) {
                callback.accept(candidate);
            } else {
                findSafeLocation(world, bounds, attemptsLeft - 1, callback);
            }
        });
=======
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
>>>>>>> 65f36955113ad1319943e3275bbe200d95f0b6a0
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
