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
import java.util.function.Consumer;

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
    }

    public void forceRTP(Player target, World world, Consumer<Boolean> callback) {
        attemptRTP(target, world, callback);
    }

    public boolean isWorldEnabled(World world) {
        return plugin.getWorldBounds().containsKey(world.getName());
    }

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
    }

    private void applyPostTeleportEffects(Player player, Location location) {
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.BLINDNESS,
                        40,
                        0,
                        false,
                        false,
                        true
                )
        );

        String coords = String.format(
                "X: %d  Y: %d  Z: %d",
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );

        Title title = Title.title(
                Component.text(coords, NamedTextColor.GOLD),
                Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofSeconds(4),
                        Duration.ofMillis(500)
                )
        );

        player.showTitle(title);
    }

    private void findSafeLocation(
            World world,
            WorldBounds bounds,
            int attemptsLeft,
            Consumer<Location> callback
    ) {
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

        world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
            int highestY = world.getHighestBlockYAt(blockX, blockZ);

            if (highestY < bounds.getMinY() || highestY > bounds.getMaxY()) {
                findSafeLocation(world, bounds, attemptsLeft - 1, callback);
                return;
            }

            Location candidate = new Location(
                    world,
                    blockX + 0.5,
                    highestY + 1.0,
                    blockZ + 0.5
            );

            if (isSafe(candidate)) {
                callback.accept(candidate);
            } else {
                findSafeLocation(world, bounds, attemptsLeft - 1, callback);
            }
        });
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();

        if (world == null) {
            return false;
        }

        Material ground = world.getBlockAt(
                location.getBlockX(),
                location.getBlockY() - 1,
                location.getBlockZ()
        ).getType();

        Material feet = world.getBlockAt(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        ).getType();

        Material head = world.getBlockAt(
                location.getBlockX(),
                location.getBlockY() + 1,
                location.getBlockZ()
        ).getType();

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
