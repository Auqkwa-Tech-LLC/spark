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

package me.lucko.spark.sponge;

import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.monitor.data.DataProvider;
import me.lucko.spark.monitor.data.MonitoringManager;
import me.lucko.spark.monitor.data.providers.TpsDataProvider;
import me.lucko.spark.sampler.ThreadDumper;
import me.lucko.spark.sampler.TickCounter;

import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.AsynchronousExecutor;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.scheduler.SynchronousExecutor;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

@Plugin(
        id = "spark",
        name = "spark",
        version = "@version@",
        description = "@desc@",
        authors = {"Luck", "sk89q"},
        dependencies = {
                // explicit dependency on spongeapi with no defined API version
                @Dependency(id = "spongeapi")
        }
)
public class SparkSpongePlugin implements CommandCallable {

    private final Game game;
    private final Path configDirectory;
    private final SpongeExecutorService syncExecutor;
    private final SpongeExecutorService asyncExecutor;

    private final SparkPlatform<CommandSource> sparkPlatform = new SparkPlatform<CommandSource>() {
        private final TickCounter tickCounter = new SpongeTickCounter(SparkSpongePlugin.this);

        private Text colorize(String message) {
            return TextSerializers.FORMATTING_CODE.deserialize(message);
        }

        private void broadcast(Text msg) {
            SparkSpongePlugin.this.game.getServer().getConsole().sendMessage(msg);
            for (Player player : SparkSpongePlugin.this.game.getServer().getOnlinePlayers()) {
                if (player.hasPermission("spark")) {
                    player.sendMessage(msg);
                }
            }
        }

        @Override
        public String getVersion() {
            return SparkSpongePlugin.class.getAnnotation(Plugin.class).version();
        }

        @Override
        public Path getPluginFolder() {
            return SparkSpongePlugin.this.configDirectory;
        }

        @Override
        public String getLabel() {
            return "spark";
        }

        @Override
        public void sendMessage(CommandSource sender, String message) {
            sender.sendMessage(colorize(message));
        }

        @Override
        public void sendMessage(String message) {
            Text msg = colorize(message);
            broadcast(msg);
        }

        @Override
        public void sendLink(String url) {
            try {
                Text msg = Text.builder(url)
                        .color(TextColors.GRAY)
                        .onClick(TextActions.openUrl(new URL(url)))
                        .build();
                broadcast(msg);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void runAsync(Runnable r) {
            SparkSpongePlugin.this.asyncExecutor.execute(r);
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

    @Inject
    public SparkSpongePlugin(Game game, @ConfigDir(sharedRoot = false) Path configDirectory, @SynchronousExecutor SpongeExecutorService syncExecutor, @AsynchronousExecutor SpongeExecutorService asyncExecutor) {
        this.game = game;
        this.configDirectory = configDirectory;
        this.syncExecutor = syncExecutor;
        this.asyncExecutor = asyncExecutor;
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        TickCounter tickCounter = this.sparkPlatform.getTickCounter();
        tickCounter.start();

        MonitoringManager monitoringManager = this.sparkPlatform.getMonitoringManager();
        monitoringManager.addDataProvider("tps", new TpsDataProvider(tickCounter));
        monitoringManager.addDataProvider("players", DataProvider.syncProvider(() -> new JsonPrimitive(this.game.getServer().getOnlinePlayers().size())));

        this.syncExecutor.scheduleWithFixedDelay(monitoringManager, 5, 5, TimeUnit.SECONDS);

        this.game.getCommandManager().register(this, this, "spark");
    }

    @Override
    public CommandResult process(CommandSource source, String arguments) {
        if (!testPermission(source)) {
            source.sendMessage(Text.builder("You do not have permission to use this command.").color(TextColors.RED).build());
            return CommandResult.empty();
        }

        this.sparkPlatform.executeCommand(source, arguments.split(" "));
        return CommandResult.empty();
    }

    @Override
    public List<String> getSuggestions(CommandSource source, String arguments, @Nullable Location<World> targetPosition) {
        return Collections.emptyList();
    }

    @Override
    public boolean testPermission(CommandSource source) {
        return source.hasPermission("spark");
    }

    @Override
    public Optional<Text> getShortDescription(CommandSource source) {
        return Optional.of(Text.of("Main spark plugin command"));
    }

    @Override
    public Optional<Text> getHelp(CommandSource source) {
        return Optional.of(Text.of("Run '/spark' to view usage."));
    }

    @Override
    public Text getUsage(CommandSource source) {
        return Text.of("Run '/spark' to view usage.");
    }
}
