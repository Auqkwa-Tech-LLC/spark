package me.lucko.spark.common.auto;

import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.SparkPlugin;
import me.lucko.spark.common.command.CommandResponseHandler;
import me.lucko.spark.common.command.modules.SamplerModule;
import me.lucko.spark.common.command.sender.CommandSender;
import me.lucko.spark.common.monitor.tick.TickStatistics;
import me.lucko.spark.common.sampler.*;
import me.lucko.spark.common.sampler.node.MergeMode;
import me.lucko.spark.common.sampler.tick.TickHook;
import me.lucko.spark.common.util.MethodDisambiguator;

import java.util.concurrent.*;
import java.util.logging.Logger;

import static me.lucko.spark.common.auto.AutoSampler.StartReason.AVG_TPS;
import static me.lucko.spark.common.auto.AutoSampler.StartReason.LATE_TICK;

public class AutoSampler implements AutoCloseable {
    public static final int CONSECUTIVE_STABLE_TICKS_THRESHOLD = 2;
    private static final double SAMPLING_INTERVAL_MILLISECONDS = 4;
    public static final int LATE_TICK_MILLISECONDS = 200;
    public static final double AVG_TPS_THRESHOLD = 19.5;
    public static final int BACKOFF_SECONDS = 10;

    private final SparkPlatform sparkPlatform;
    private final SamplerModule samplerModule;
    private final TickStatistics tickStatistics;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final CommandResponseHandler commandResponseHandler;
    private final Logger logger;

    private ScheduledFuture<?> lateTickHandler;
    private int consecutiveStableTicks = 0;
    private Sampler sampler;

    private long lastProfileMilliseconds = 0;
    private long lastTickMilliseconds = 0;

    enum StartReason {
        AVG_TPS("Starting profiler because average tps for the past 5 seconds < " + AVG_TPS_THRESHOLD),
        LATE_TICK("Starting profiler because tick took more than " + LATE_TICK_MILLISECONDS + "ms");

        private final String message;

        StartReason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public AutoSampler(
            SparkPlatform sparkPlatform,
            SamplerModule samplerModule,
            TickStatistics tickStatistics,
            Logger logger,
            TickHook tickHook) {
        this.sparkPlatform = sparkPlatform;
        this.samplerModule = samplerModule;
        this.tickStatistics = tickStatistics;
        this.logger = logger;

        @SuppressWarnings("OptionalGetWithoutIsPresent")
        CommandSender consoleCommandSender = sparkPlatform.getPlugin().getSendersWithPermission("spark.console")
                .reduce((first, second) -> second)
                .get();
        commandResponseHandler = new CommandResponseHandler(sparkPlatform, consoleCommandSender);

        tickHook.addCallback(this::onTick);
    }

    @Override
    public void close() {
        if (lateTickHandler != null) {
            lateTickHandler.cancel(true);
        }
    }

    private void onTick(int tick) {
        // Ignore first 10 seconds of ticks (allow the server to stabilize when booting)
        if (tick < 20 * 10) {
            return;
        }

        if (lateTickHandler != null) {
            lateTickHandler.cancel(false);
        }

        long now = System.currentTimeMillis();
        if (lastTickMilliseconds != -1) {
            if (now - lastTickMilliseconds < LATE_TICK_MILLISECONDS) {
                consecutiveStableTicks++;
            } else {
                consecutiveStableTicks = 0;
            }
        }

        if (isProfiling() && consecutiveStableTicks == CONSECUTIVE_STABLE_TICKS_THRESHOLD && tickStatistics.tps5Sec() > AVG_TPS_THRESHOLD) {
            logger.info("Stopping profiler because past " + CONSECUTIVE_STABLE_TICKS_THRESHOLD + " were under " + LATE_TICK_MILLISECONDS + "ms and average tps for the past 5 seconds > " + AVG_TPS_THRESHOLD);
            stopProfiling();
        }

        lateTickHandler = scheduler.schedule(this::handleLateTick, LATE_TICK_MILLISECONDS, TimeUnit.MILLISECONDS);

        if (tickStatistics.tps5Sec() < AVG_TPS_THRESHOLD) {
            startProfiling(AVG_TPS);
        }

        this.lastTickMilliseconds = now;
    }

    private void handleLateTick() {
        if (!isProfiling()) {
            startProfiling(LATE_TICK);
        }
    }

    private void startProfiling(StartReason reason) {
        if (System.currentTimeMillis() - lastProfileMilliseconds < 1000 * BACKOFF_SECONDS) {
            return;
        }

        if (sampler != null || samplerModule.getActiveSampler() != null) return;

        logger.info(reason.getMessage());

        SparkPlugin plugin = sparkPlatform.getPlugin();
        ThreadDumper defaultThreadDumper = plugin.getDefaultThreadDumper();

        SamplerBuilder builder = new SamplerBuilder();
        builder.threadDumper(defaultThreadDumper);
        builder.threadGrouper(ThreadGrouper.BY_POOL);
        builder.samplingInterval(SAMPLING_INTERVAL_MILLISECONDS);
        builder.ignoreSleeping(false);
        builder.ignoreNative(false);
        builder.samplingInterval(0.1);
        builder.completeAfter(1, TimeUnit.MINUTES);

        final Sampler sampler = builder.start();
        this.sampler = sampler;
        samplerModule.setActiveSampler(sampler);

        CompletableFuture<Sampler> samplerFuture = sampler.getFuture();
        samplerFuture.whenCompleteAsync((s, throwable) -> {
            if (this.sampler == sampler) this.sampler = null;
            if (samplerModule.getActiveSampler() == sampler) samplerModule.setActiveSampler(null);
            lastProfileMilliseconds = System.currentTimeMillis();
        });
    }

    private void stopProfiling() {
        Sampler sampler = this.sampler;

        if (sampler == null) return;
        if (samplerModule.getActiveSampler() != sampler) return;

        sampler.cancel();
        this.sampler = null;
        samplerModule.setActiveSampler(null);
        lastProfileMilliseconds = System.currentTimeMillis();

        String comment = "AutoSampler";

        ThreadNodeOrder nodeOrder = ThreadNodeOrder.BY_TIME;
        MethodDisambiguator methodDisambiguator = new MethodDisambiguator();
        MergeMode mergeMode = MergeMode.sameMethod(methodDisambiguator);

        samplerModule.handleUpload(
                sparkPlatform,
                commandResponseHandler,
                sampler,
                nodeOrder,
                comment,
                mergeMode
        );
    }

    private boolean isProfiling() {
        return sampler != null;
    }
}
