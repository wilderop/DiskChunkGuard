package com.yourname.diskchunkguard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;

public final class DiskChunkGuard extends JavaPlugin implements Listener {

    private boolean blocking = false;
    private int thresholdPercent = 85;
    private String warningMessage = "&cServer storage is nearly full! New terrain generation is disabled.";
    private String monitorPath = ""; // Empty = auto-detect server root

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("dcgreload").setExecutor((sender, cmd, label, args) -> {
            loadConfig();
            sender.sendMessage(ChatColor.GREEN + "DiskChunkGuard reloaded!");
            return true;
        });
        getLogger().info("DiskChunkGuard enabled - monitoring disk for chunk protection.");
        // Periodic check
        Bukkit.getScheduler().runTaskTimer(this, this::checkDisk, 0L, 600L); // Every 30 seconds
    }

    private void loadConfig() {
        thresholdPercent = getConfig().getInt("threshold-percent", 85);
        warningMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message", warningMessage));
        monitorPath = getConfig().getString("monitor-path", "");
    }

    private void checkDisk() {
        try {
            String path = monitorPath.isEmpty() ? getDataFolder().getParentFile().getParent() : monitorPath;
            FileStore store = Files.getFileStore(Paths.get(path));
            long total = store.getTotalSpace();
            long used = total - store.getUsableSpace();
            int percent = (int) ((used * 100) / total);
            blocking = percent >= thresholdPercent;
            if (blocking && percent % 5 == 0) { // Log occasionally
                getLogger().warning("Disk usage at " + percent + "% - blocking new chunks!");
            }
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to check disk usage", e);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!blocking || !event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        if (chunk.getWorld().getPlayers().isEmpty()) return; // Optional: only block for players

        event.setCancelled(true); // Prevents new generation
        // Message the nearest player or all in world (simple approach)
        chunk.getWorld().getPlayers().forEach(p -> p.sendMessage(warningMessage));
    }
}
