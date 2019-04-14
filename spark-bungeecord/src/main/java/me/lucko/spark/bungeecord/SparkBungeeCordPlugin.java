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

package me.lucko.spark.bungeecord;

import com.google.gson.JsonPrimitive;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.monitor.data.MonitoringManager;
import me.lucko.spark.sampler.ThreadDumper;
import me.lucko.spark.sampler.TickCounter;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SparkBungeeCordPlugin extends Plugin {

    private final SparkPlatform<CommandSender> sparkPlatform = new SparkPlatform<CommandSender>() {
        private BaseComponent[] colorize(String message) {
            return TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message));
        }

        private void broadcast(BaseComponent... msg) {
            getProxy().getConsole().sendMessage(msg);
            for (ProxiedPlayer player : getProxy().getPlayers()) {
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
            return "sparkb";
        }

        @Override
        public void sendMessage(CommandSender sender, String message) {
            sender.sendMessage(colorize(message));
        }

        @Override
        public void sendMessage(String message) {
            broadcast(colorize(message));
        }

        @Override
        public void sendLink(String url) {
            TextComponent component = new TextComponent(url);
            component.setColor(ChatColor.GRAY);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            broadcast(component);
        }

        @Override
        public void runAsync(Runnable r) {
            getProxy().getScheduler().runAsync(SparkBungeeCordPlugin.this, r);
        }

        @Override
        public ThreadDumper getDefaultThreadDumper() {
            return ThreadDumper.ALL;
        }

        @Override
        public TickCounter getTickCounter() {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    public void onEnable() {
        MonitoringManager monitoringManager = this.sparkPlatform.getMonitoringManager();
        monitoringManager.addDataProvider("players", () -> new JsonPrimitive(getProxy().getPlayers().size()));

        getProxy().getScheduler().schedule(this, monitoringManager, 5, 5, TimeUnit.SECONDS);

        getProxy().getPluginManager().registerCommand(this, new SparkCommand());
    }

    private final class SparkCommand extends Command implements TabExecutor {
        public SparkCommand() {
            super("sparkb", null, "sparkbungee");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!sender.hasPermission("spark")) {
                TextComponent msg = new TextComponent("You do not have permission to use this command.");
                msg.setColor(ChatColor.RED);
                sender.sendMessage(msg);
                return;
            }

            SparkBungeeCordPlugin.this.sparkPlatform.executeCommand(sender, args);
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
            if (!sender.hasPermission("spark")) {
                return Collections.emptyList();
            }
            return SparkBungeeCordPlugin.this.sparkPlatform.tabCompleteCommand(sender, args);
        }
    }
}
