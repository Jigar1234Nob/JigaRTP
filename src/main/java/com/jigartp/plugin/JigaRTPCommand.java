package com.jigartp.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class JigaRTPCommand implements CommandExecutor {

    private final JigaRTP plugin;

    public JigaRTPCommand(JigaRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /jigartp reload — admin-only config reload. Works from console too.
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("jigartp.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }
            plugin.reloadJigaConfig();
            sender.sendMessage(ChatColor.GREEN + "JigaRTP config reloaded.");
            return true;
        }

        // /jigartp <player> — admin force-RTP on someone else. Works from console too.
        if (args.length > 0) {
            if (!sender.hasPermission("jigartp.others")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to teleport other players.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player \"" + args[0] + "\" is not online.");
                return true;
            }

            World world = target.getWorld();
            if (!plugin.getRtpManager().isWorldEnabled(world)) {
                sender.sendMessage(ChatColor.RED + "RTP is not enabled in " + target.getName() + "'s current world.");
                return true;
            }

            // Admin force-RTP bypasses cooldown and skips the countdown — it's instant.
            boolean success = plugin.getRtpManager().forceRTP(target, world);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Force-teleported " + target.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not find a safe location for " + target.getName() + ". Please try again.");
            }
            return true;
        }

        // /jigartp — regular self RTP, players only.
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command. Console must specify a player: /jigartp <player>");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("jigartp.use")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        World world = player.getWorld();

        if (!plugin.getRtpManager().isWorldEnabled(world)) {
            player.sendMessage(ChatColor.RED + "RTP is not enabled in this world.");
            return true;
        }

        long remaining = plugin.getRtpManager().getRemainingCooldown(player);
        if (remaining > 0) {
            player.sendMessage(ChatColor.RED + "You must wait " + remaining + " more second(s) before using /jigartp again.");
            return true;
        }

        if (plugin.getRtpManager().isCountingDown(player)) {
            player.sendMessage(ChatColor.RED + "You are already being teleported.");
            return true;
        }

        plugin.getRtpManager().startCountdown(player, world);
        return true;
    }
}
