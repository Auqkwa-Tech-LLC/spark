/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.bukkit;

import com.google.gson.JsonPrimitive;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.monitor.data.DataProvider;
import me.lucko.spark.monitor.data.MonitoringManager;
import me.lucko.spark.monitor.data.providers.TpsDataProvider;
import me.lucko.spark.sampler.ThreadDumper;
import me.lucko.spark.sampler.TickCounter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class SparkBukkitPlugin extends JavaPlugin {

    private final SparkPlatform<CommandSender> sparkPlatform = new SparkPlatform<CommandSender>() {
        private final TickCounter tickCounter = new BukkitTickCounter(SparkBukkitPlugin.this);

        private String colorize(String message) {
            return ChatColor.translateAlternateColorCodes('&', message);
        }

        private void broadcast(String msg) {
            getServer().getConsoleSender().sendMessage(msg);
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.hasPermission("spark")) {
                    player.sendMessage(msg);
                }
            }
        }

        @Override
        public String getVersion() {
            return getDescription().getVersion();
        }

        @Override
        public Path getPluginFolder() {
            return getDataFolder().toPath();
        }

        @Override
        public String getLabel() {
            return "spark";
        }

        @Override
        public void sendMessage(CommandSender sender, String message) {
            sender.sendMessage(colorize(message));
        }

        @Override
        public void sendMessage(String message) {
            String msg = colorize(message);
            broadcast(msg);
        }

        @Override
        public void sendLink(String url) {
            String msg = colorize("&7" + url);
            broadcast(msg);
        }

        @Override
        public void runAsync(Runnable r) {
            getServer().getScheduler().runTaskAsynchronously(SparkBukkitPlugin.this, r);
        }

        @Override
        public ThreadDumper getDefaultThreadDumper() {
            return new ThreadDumper.Specific(new long[]{Thread.currentThread().getId()});
        }

        @Override
        public TickCounter getTickCounter() {
            return this.tickCounter;
        }
    };

    @Override
    public void onEnable() {
        TickCounter tickCounter = this.sparkPlatform.getTickCounter();
        tickCounter.start();

        MonitoringManager monitoringManager = this.sparkPlatform.getMonitoringManager();
        monitoringManager.addDataProvider("tps", new TpsDataProvider(tickCounter));
        monitoringManager.addDataProvider("players", DataProvider.syncProvider(() -> new JsonPrimitive(getServer().getOnlinePlayers().size())));
        monitoringManager.addDataProvider("entities", new EntityDataProvider(getServer()));

        getServer().getScheduler().runTaskTimer(this, monitoringManager, 3, 20 * 5);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spark")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        this.sparkPlatform.executeCommand(sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("spark")) {
            return Collections.emptyList();
        }
        return this.sparkPlatform.tabCompleteCommand(sender, args);
    }
}
