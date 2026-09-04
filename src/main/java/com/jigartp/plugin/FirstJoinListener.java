package com.jigartp.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class FirstJoinListener implements Listener {

    private final JigaRTP plugin;

    public FirstJoinListener(JigaRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isFirstJoinRtp()) {
            return;
        }

        Player player = event.getPlayer();

        // hasPlayedBefore() is false only on a player's very first join to the server.
        if (player.hasPlayedBefore()) {
            return;
        }

        World targetWorld;
        String configuredWorld = plugin.getFirstJoinWorld();

        if (configuredWorld == null || configuredWorld.equalsIgnoreCase("default-world")) {
            targetWorld = player.getWorld();
        } else {
            World namedWorld = Bukkit.getWorld(configuredWorld);
            targetWorld = (namedWorld != null) ? namedWorld : player.getWorld();
        }

        if (!plugin.getRtpManager().isWorldEnabled(targetWorld)) {
            return;
        }

        // Run a tick later so the player has fully finished loading in before we teleport them.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean success = plugin.getRtpManager().attemptRTP(player, targetWorld);
            if (success) {
                player.sendMessage(ChatColor.GREEN + "Welcome! You've been randomly teleported to get you started.");
            }
        }, 20L);
    }
}
