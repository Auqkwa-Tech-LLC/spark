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

import me.lucko.spark.sampler.TickCounter;

import java.util.HashSet;
import java.util.Set;

public class SpongeTickCounter implements TickCounter, Runnable {
    private final SparkSpongePlugin plugin;
    private org.spongepowered.api.scheduler.Task task;

    private final Set<TickTask> tasks = new HashSet<>();
    private int tick = 0;

    public SpongeTickCounter(SparkSpongePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (TickTask r : this.tasks){
            r.onTick(this);
        }
        this.tick++;
    }

    @Override
    public void start() {
        this.task = org.spongepowered.api.scheduler.Task.builder().intervalTicks(1).name("spark-ticker").execute(this).submit(this.plugin);
    }

    @Override
    public void close() {
        this.task.cancel();
    }

    @Override
    public int getCurrentTick() {
        return this.tick;
    }

    @Override
    public void addTickTask(TickTask runnable) {
        this.tasks.add(runnable);
    }

    @Override
    public void removeTickTask(TickTask runnable) {
        this.tasks.remove(runnable);
    }
}
