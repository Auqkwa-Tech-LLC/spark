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

package me.lucko.spark.common.command.modules;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.command.Command;
import me.lucko.spark.common.command.CommandModule;
import me.lucko.spark.common.command.tabcomplete.TabCompleter;
import me.lucko.spark.monitor.TickMonitor;

import java.util.function.Consumer;

public class TickMonitoringModule<S> implements CommandModule<S> {

    /** The tick monitor instance currently running, if any */
    private ReportingTickMonitor activeTickMonitor = null;

    @Override
    public void registerCommands(Consumer<Command<S>> consumer) {
        consumer.accept(Command.<S>builder()
                .aliases("tickmonitoring")
                .argumentUsage("threshold", "percentage increase")
                .argumentUsage("without-gc", null)
                .executor((platform, sender, arguments) -> {
                    if (this.activeTickMonitor == null) {

                        int threshold = arguments.intFlag("threshold");
                        if (threshold == -1) {
                            threshold = 100;
                        }

                        try {
                            this.activeTickMonitor = new ReportingTickMonitor(platform, threshold, !arguments.boolFlag("without-gc"));
                            platform.getTickCounter().addTickTask(this.activeTickMonitor);
                        } catch (UnsupportedOperationException e) {
                            platform.sendPrefixedMessage(sender, "&cNot supported!");
                        }
                    } else {
                        platform.getTickCounter().removeTickTask(this.activeTickMonitor);
                        this.activeTickMonitor.close();
                        this.activeTickMonitor = null;
                        platform.sendPrefixedMessage("&7Tick monitor disabled.");
                    }
                })
                .tabCompleter((platform, sender, arguments) -> TabCompleter.completeForOpts(arguments, "--threshold", "--without-gc"))
                .build()
        );
    }

    private class ReportingTickMonitor extends TickMonitor {
        private final SparkPlatform<S> platform;

        ReportingTickMonitor(SparkPlatform<S> platform, int percentageChangeThreshold, boolean monitorGc) {
            super(platform.getTickCounter(), percentageChangeThreshold, monitorGc);
            this.platform = platform;
        }

        @Override
        protected void sendMessage(String message) {
            this.platform.sendPrefixedMessage(message);
        }
    }
}
