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

package me.lucko.spark.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.lucko.spark.common.activitylog.ActivityLog;
import me.lucko.spark.common.command.Arguments;
import me.lucko.spark.common.command.Command;
import me.lucko.spark.common.command.CommandModule;
import me.lucko.spark.common.command.CommandResponseHandler;
import me.lucko.spark.common.command.modules.ActivityLogModule;
import me.lucko.spark.common.command.modules.GcMonitoringModule;
import me.lucko.spark.common.command.modules.HealthModule;
import me.lucko.spark.common.command.modules.HeapAnalysisModule;
import me.lucko.spark.common.command.modules.SamplerModule;
import me.lucko.spark.common.command.modules.TickMonitoringModule;
import me.lucko.spark.common.command.sender.CommandSender;
import me.lucko.spark.common.command.tabcomplete.CompletionSupplier;
import me.lucko.spark.common.command.tabcomplete.TabCompleter;
import me.lucko.spark.common.grafana.GrafanaClient;
import me.lucko.spark.common.monitor.cpu.CpuMonitor;
import me.lucko.spark.common.monitor.memory.GarbageCollectorStatistics;
import me.lucko.spark.common.monitor.tick.TickStatistics;
import me.lucko.spark.common.sampler.tick.TickHook;
import me.lucko.spark.common.sampler.tick.TickReporter;
import me.lucko.spark.common.util.BytebinClient;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import okhttp3.OkHttpClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Abstract spark implementation used by all platforms.
 */
public class SparkPlatform {

    /** The URL of the viewer frontend */
    public static final String VIEWER_URL = System.getProperty("me.lucko.spark.viewer.url", "https://spark.lucko.me/#");
    public static final String BYTEBIN_URL = System.getProperty("me.lucko.spark.bytebin.url", "https://bytebin.lucko.me");

    /** The shared okhttp client */
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();
    /** The bytebin instance used by the platform */
    public static final BytebinClient BYTEBIN_CLIENT = new BytebinClient(OK_HTTP_CLIENT, BYTEBIN_URL, "spark-plugin");

    public static final GrafanaClient GRAFANA_CLIENT = GrafanaClient.fromEnvironment(OK_HTTP_CLIENT);

    private final SparkPlugin plugin;
    private final List<CommandModule> commandModules;
    private final List<Command> commands;
    private final ActivityLog activityLog;
    private final TickHook tickHook;
    private final TickReporter tickReporter;
    private final TickStatistics tickStatistics;
    private Map<String, GarbageCollectorStatistics> startupGcStatistics = ImmutableMap.of();
    private long serverNormalOperationStartTime;

    public SparkPlatform(SparkPlugin plugin) {
        this.plugin = plugin;

        this.commandModules = ImmutableList.of(
                new SamplerModule(),
                new HealthModule(),
                new TickMonitoringModule(),
                new GcMonitoringModule(),
                new HeapAnalysisModule(),
                new ActivityLogModule()
        );

        ImmutableList.Builder<Command> commandsBuilder = ImmutableList.builder();
        for (CommandModule module : this.commandModules) {
            module.registerCommands(commandsBuilder::add);
        }
        this.commands = commandsBuilder.build();

        this.activityLog = new ActivityLog(plugin.getPluginDirectory().resolve("activity.json"));
        this.activityLog.load();

        this.tickHook = plugin.createTickHook();
        this.tickReporter = plugin.createTickReporter();
        this.tickStatistics = this.tickHook != null ? new TickStatistics() : null;
    }

    public void enable() {
        if (this.tickHook != null) {
            this.tickHook.addCallback(this.tickStatistics);
            this.tickHook.start();
        }
        if (this.tickReporter != null) {
            this.tickReporter.addCallback(this.tickStatistics);
            this.tickReporter.start();
        }
        CpuMonitor.ensureMonitoring();

        // poll startup GC statistics after plugins & the world have loaded
        this.plugin.executeAsync(() -> {
            this.startupGcStatistics = GarbageCollectorStatistics.pollStats();
            this.serverNormalOperationStartTime = System.currentTimeMillis();
        });
    }

    public void disable() {
        if (this.tickHook != null) {
            this.tickHook.close();
        }
        if (this.tickReporter != null) {
            this.tickReporter.close();
        }

        for (CommandModule module : this.commandModules) {
            module.close();
        }
    }

    public SparkPlugin getPlugin() {
        return this.plugin;
    }

    public ActivityLog getActivityLog() {
        return this.activityLog;
    }

    public TickHook getTickHook() {
        return this.tickHook;
    }

    public TickReporter getTickReporter() {
        return this.tickReporter;
    }

    public TickStatistics getTickStatistics() {
        return this.tickStatistics;
    }

    public Map<String, GarbageCollectorStatistics> getStartupGcStatistics() {
        return this.startupGcStatistics;
    }

    public long getServerNormalOperationStartTime() {
        return this.serverNormalOperationStartTime;
    }

    public void executeCommand(CommandSender sender, String[] args) {
        CommandResponseHandler resp = new CommandResponseHandler(this, sender);

        if (!sender.hasPermission("spark")) {
            resp.replyPrefixed(TextComponent.of("You do not have permission to use this command.", TextColor.RED));
            return;
        }

        if (args.length == 0) {
            resp.replyPrefixed(TextComponent.builder("")
                    .append(TextComponent.of("spark", TextColor.WHITE))
                    .append(TextComponent.space())
                    .append(TextComponent.of("v" + getPlugin().getVersion(), TextColor.GRAY))
                    .build()
            );
            resp.replyPrefixed(TextComponent.builder("").color(TextColor.GRAY)
                    .append(TextComponent.of("Use "))
                    .append(TextComponent.builder("/" + getPlugin().getCommandName() + " help")
                            .color(TextColor.WHITE)
                            .decoration(TextDecoration.UNDERLINED, true)
                            .clickEvent(ClickEvent.runCommand("/" + getPlugin().getCommandName() + " help"))
                            .build()
                    )
                    .append(TextComponent.of(" to view usage information."))
                    .build()
            );
            return;
        }

        ArrayList<String> rawArgs = new ArrayList<>(Arrays.asList(args));
        String alias = rawArgs.remove(0).toLowerCase();

        for (Command command : this.commands) {
            if (command.aliases().contains(alias)) {
                try {
                    command.executor().execute(this, sender, resp, new Arguments(rawArgs));
                } catch (IllegalArgumentException e) {
                    resp.replyPrefixed(TextComponent.of(e.getMessage(), TextColor.RED));
                }
                return;
            }
        }

        sendUsage(resp);
    }

    public List<String> tabCompleteCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("spark")) {
            return Collections.emptyList();
        }

        List<String> arguments = new ArrayList<>(Arrays.asList(args));

        if (args.length <= 1) {
            List<String> mainCommands = this.commands.stream().map(c -> c.aliases().get(0)).collect(Collectors.toList());
            return TabCompleter.create()
                    .at(0, CompletionSupplier.startsWith(mainCommands))
                    .complete(arguments);
        }

        String alias = arguments.remove(0);
        for (Command command : this.commands) {
            if (command.aliases().contains(alias)) {
                return command.tabCompleter().completions(this, sender, arguments);
            }
        }

        return Collections.emptyList();
    }

    private void sendUsage(CommandResponseHandler sender) {
        sender.replyPrefixed(TextComponent.builder("")
                .append(TextComponent.of("spark", TextColor.WHITE))
                .append(TextComponent.space())
                .append(TextComponent.of("v" + getPlugin().getVersion(), TextColor.GRAY))
                .build()
        );
        for (Command command : this.commands) {
            String usage = "/" + getPlugin().getCommandName() + " " + command.aliases().get(0);
            ClickEvent clickEvent = ClickEvent.suggestCommand(usage);
            sender.reply(TextComponent.builder("")
                    .append(TextComponent.builder(">").color(TextColor.GOLD).decoration(TextDecoration.BOLD, true).build())
                    .append(TextComponent.space())
                    .append(TextComponent.builder(usage).color(TextColor.GRAY).clickEvent(clickEvent).build())
                    .build()
            );
            for (Command.ArgumentInfo arg : command.arguments()) {
                if (arg.requiresParameter()) {
                    sender.reply(TextComponent.builder("       ")
                            .append(TextComponent.of("[", TextColor.DARK_GRAY))
                            .append(TextComponent.of("--" + arg.argumentName(), TextColor.GRAY))
                            .append(TextComponent.space())
                            .append(TextComponent.of("<" + arg.parameterDescription() + ">", TextColor.DARK_GRAY))
                            .append(TextComponent.of("]", TextColor.DARK_GRAY))
                            .build()
                    );
                } else {
                    sender.reply(TextComponent.builder("       ")
                            .append(TextComponent.of("[", TextColor.DARK_GRAY))
                            .append(TextComponent.of("--" + arg.argumentName(), TextColor.GRAY))
                            .append(TextComponent.of("]", TextColor.DARK_GRAY))
                            .build()
                    );
                }
            }
        }
    }

}
