package com.jigartp.plugin;

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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // /jigartp reload — admin-only config reload.
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("jigartp.reload")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to do that.");
                return true;
            }
            plugin.reloadJigaConfig();
            player.sendMessage(ChatColor.GREEN + "JigaRTP config reloaded.");
            return true;
        }

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
