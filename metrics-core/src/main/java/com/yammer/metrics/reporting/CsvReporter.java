package com.yammer.metrics.reporting;

import com.yammer.metrics.core.*;
import com.yammer.metrics.stats.Snapshot;
import com.yammer.metrics.util.MetricPredicate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CsvReporter extends AbstractPollingReporter implements
                                                         MetricProcessor<CsvReporter.Context> {
    private final MetricPredicate predicate;
    private final File outputDir;
    private final Map<MetricName, PrintStream> streamMap;
    private final Clock clock;
    private long startTime;

    public CsvReporter(File outputDir,
                       MetricsRegistry metricsRegistry,
                       MetricPredicate predicate) throws Exception {
        this(outputDir, metricsRegistry, predicate, Clock.DEFAULT);
    }

    public CsvReporter(File outputDir,
                       MetricsRegistry metricsRegistry,
                       MetricPredicate predicate,
                       Clock clock) throws Exception {
        super(metricsRegistry, "csv-reporter");
        this.outputDir = outputDir;
        this.predicate = predicate;
        this.streamMap = new HashMap<MetricName, PrintStream>();
        this.startTime = 0L;
        this.clock = clock;
    }

    public CsvReporter(File outputDir, MetricsRegistry metricsRegistry)
            throws Exception {
        this(outputDir, metricsRegistry, MetricPredicate.ALL);
    }

    private PrintStream getPrintStream(MetricName metricName, String header)
            throws IOException {
        PrintStream stream;
        synchronized (streamMap) {
            stream = streamMap.get(metricName);
            if (stream == null) {
                stream = createStreamForMetric(metricName);
                streamMap.put(metricName, stream);
                stream.println(header);
            }
        }
        return stream;
    }

    /**
     * Override to do tricks (such as testing).
     */
    protected PrintStream createStreamForMetric(MetricName metricName) throws IOException {
        final File newFile = new File(outputDir, metricName.getName() + ".csv");
        if (newFile.createNewFile()) {
            return new PrintStream(new FileOutputStream(newFile));
        }
        throw new IOException("Unable to create " + newFile);
    }

    @Override
    public void run() {
        final long time = TimeUnit.MILLISECONDS.toSeconds(clock.time() - startTime);
        final Set<Entry<MetricName, Metric>> metrics = getMetricsRegistry().allMetrics().entrySet();
        try {
            for (Entry<MetricName, Metric> entry : metrics) {
                final MetricName metricName = entry.getKey();
                final Metric metric = entry.getValue();
                if (predicate.matches(metricName, metric)) {
                    final Context context = new Context() {
                        @Override
                        public PrintStream getStream(String header) throws IOException {
                            final PrintStream stream = getPrintStream(metricName, header);
                            stream.print(time);
                            stream.print(',');
                            return stream;
                        }

                    };
                    metric.processWith(this, entry.getKey(), context);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface Context {
        PrintStream getStream(String header) throws IOException;
    }

    @Override
    public void processMeter(MetricName name, Metered meter, Context context) throws IOException {
        final PrintStream stream = context.getStream(
                "# time,count,1 min rate,mean rate,5 min rate,15 min rate");
        stream.append(new StringBuilder()
                              .append(meter.count()).append(',')
                              .append(meter.oneMinuteRate()).append(',')
                              .append(meter.meanRate()).append(',')
                              .append(meter.fiveMinuteRate()).append(',')
                              .append(meter.fifteenMinuteRate()).toString())
              .println();
        stream.flush();
    }

    @Override
    public void processCounter(MetricName name, Counter counter, Context context) throws IOException {
        final PrintStream stream = context.getStream("# time,count");
        stream.println(counter.count());
        stream.flush();
    }

    @Override
    public void processHistogram(MetricName name, Histogram histogram, Context context) throws IOException {
        final PrintStream stream = context.getStream("# time,min,max,mean,median,stddev,95%,99%,99.9%");
        final Snapshot snapshot = histogram.getSnapshot();
        stream.append(new StringBuilder()
                              .append(histogram.min()).append(',')
                              .append(histogram.max()).append(',')
                              .append(histogram.mean()).append(',')
                              .append(snapshot.getMedian()).append(',')
                              .append(histogram.stdDev()).append(',')
                              .append(snapshot.get95thPercentile()).append(',')
                              .append(snapshot.get99thPercentile()).append(',')
                              .append(snapshot.get999thPercentile()).toString())
                .println();
        stream.println();
        stream.flush();
    }

    @Override
    public void processTimer(MetricName name, Timer timer, Context context) throws IOException {
        final PrintStream stream = context.getStream("# time,min,max,mean,median,stddev,95%,99%,99.9%");
        final Snapshot snapshot = timer.getSnapshot();
        stream.append(new StringBuilder()
                              .append(timer.min()).append(',')
                              .append(timer.max()).append(',')
                              .append(timer.mean()).append(',')
                              .append(snapshot.getMedian()).append(',')
                              .append(timer.stdDev()).append(',')
                              .append(snapshot.get95thPercentile()).append(',')
                              .append(snapshot.get99thPercentile()).append(',')
                              .append(snapshot.get999thPercentile()).toString())
                .println();
        stream.flush();
    }

    @Override
    public void processGauge(MetricName name, Gauge<?> gauge, Context context) throws IOException {
        final PrintStream stream = context.getStream("# time,value");
        stream.println(gauge.value());
        stream.flush();
    }

    @Override
    public void start(long period, TimeUnit unit) {
        this.startTime = clock.time();
        super.start(period, unit);
    }

    @Override
    public void shutdown() {
        try {
            super.shutdown();
        } finally {
            for (PrintStream out : streamMap.values()) {
                out.close();
            }
        }
    }
}
