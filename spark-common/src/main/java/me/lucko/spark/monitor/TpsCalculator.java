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

package me.lucko.spark.monitor;

import me.lucko.spark.sampler.TickCounter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/**
 * Calculates the servers TPS (ticks per second) rate.
 *
 * <p>The code use to calculate the TPS is the same as the code used by the Minecraft server itself.
 * This means that this class will output values the same as the /tps command.</p>
 *
 * <p>We calculate our own values instead of pulling them from the server for two reasons. Firstly,
 * it's easier - pulling from the server requires reflection code on each of the platforms, we'd
 * rather avoid that. Secondly, it allows us to generate rolling averages over a shorter period of
 * time.</p>
 */
public class TpsCalculator implements TickCounter.TickTask {

    private static final long SEC_IN_NANO = TimeUnit.SECONDS.toNanos(1);
    private static final int TPS = 20;
    private static final int SAMPLE_INTERVAL = 20;
    private static final BigDecimal TPS_BASE = new BigDecimal(SEC_IN_NANO).multiply(new BigDecimal((long) SAMPLE_INTERVAL));

    private final RollingAverage tps5S = new RollingAverage(5);
    private final RollingAverage tps10S = new RollingAverage(10);
    private final RollingAverage tps1M = new RollingAverage(60);
    private final RollingAverage tps5M = new RollingAverage(60 * 5);
    private final RollingAverage tps15M = new RollingAverage(60 * 15);

    private final RollingAverage[] averages = new RollingAverage[]{
            this.tps5S, this.tps10S, this.tps1M, this.tps5M, this.tps15M
    };

    private long last = 0;

    // called every tick
    @Override
    public void onTick(TickCounter counter) {
        if (counter.getCurrentTick() % SAMPLE_INTERVAL != 0) {
            return;
        }

        long now = System.nanoTime();

        if (this.last == 0) {
            this.last = now;
            return;
        }

        long diff = now - this.last;
        BigDecimal currentTps = TPS_BASE.divide(new BigDecimal(diff), 30, RoundingMode.HALF_UP);

        for (RollingAverage rollingAverage : this.averages) {
            rollingAverage.add(currentTps, diff);
        }

        this.last = now;
    }

    public RollingAverage avg5Sec() {
        return this.tps5S;
    }

    public RollingAverage avg10Sec() {
        return this.tps10S;
    }

    public RollingAverage avg1Min() {
        return this.tps1M;
    }

    public RollingAverage avg5Min() {
        return this.tps5M;
    }

    public RollingAverage avg15Min() {
        return this.tps15M;
    }

    public String toFormattedString() {
        return formatTps(this.tps5S.getAverage()) + ", " +
                formatTps(this.tps10S.getAverage()) + ", " +
                formatTps(this.tps1M.getAverage()) + ", " +
                formatTps(this.tps5M.getAverage()) + ", " +
                formatTps(this.tps15M.getAverage());
    }

    public static String formatTps(double tps) {
        StringBuilder sb = new StringBuilder();
        if (tps > 18.0) {
            sb.append("&a");
        } else if (tps > 16.0) {
            sb.append("&e");
        } else {
            sb.append("&c");
        }
        if (tps > 20.0) {
            sb.append('*');
        }
        return sb.append(Math.min(Math.round(tps * 100.0) / 100.0, 20.0)).toString();
    }

    /**
     * Rolling average calculator taken.
     *
     * <p>This code is taken from PaperMC/Paper, licensed under MIT.</p>
     *
     * @author aikar (PaperMC) https://github.com/PaperMC/Paper/blob/master/Spigot-Server-Patches/0021-Further-improve-server-tick-loop.patch
     */
    public static class RollingAverage {
        private final int size;
        private long time;
        private BigDecimal total;
        private int index = 0;
        private final BigDecimal[] samples;
        private final long[] times;

        RollingAverage(int size) {
            this.size = size;
            this.time = size * SEC_IN_NANO;
            this.total = new BigDecimal((long) TPS).multiply(new BigDecimal(SEC_IN_NANO)).multiply(new BigDecimal((long) size));
            this.samples = new BigDecimal[size];
            this.times = new long[size];
            for (int i = 0; i < size; i++) {
                this.samples[i] = new BigDecimal((long) TPS);
                this.times[i] = SEC_IN_NANO;
            }
        }

        public void add(BigDecimal x, long t) {
            this.time -= this.times[this.index];
            this.total = this.total.subtract(this.samples[this.index].multiply(new BigDecimal(this.times[this.index])));
            this.samples[this.index] = x;
            this.times[this.index] = t;
            this.time += t;
            this.total = this.total.add(x.multiply(new BigDecimal(t)));
            if (++this.index == this.size) {
                this.index = 0;
            }
        }

        public double getAverage() {
            return this.total.divide(new BigDecimal(this.time), 30, RoundingMode.HALF_UP).doubleValue();
        }
    }

}
