package com.yammer.metrics.jetty;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.bio.SocketConnector;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class InstrumentedSocketConnector extends SocketConnector {
    private final Timer duration;
    private final Meter accepts, connects, disconnects;
    private final Counter connections;

    public InstrumentedSocketConnector(int port) {
        this(Metrics.defaultRegistry(), port);
    }

    public InstrumentedSocketConnector(MetricsRegistry registry, int port) {
        super();
        setPort(port);
        this.duration = registry.newTimer(SocketConnector.class,
                                                    "connection-duration",
                                                    Integer.toString(port),
                                                    TimeUnit.MILLISECONDS,
                                                    TimeUnit.SECONDS);
        this.accepts = registry.newMeter(SocketConnector.class,
                                         "accepts",
                                         Integer.toString(port),
                                         "connections",
                                         TimeUnit.SECONDS);
        this.connects = registry.newMeter(SocketConnector.class,
                                                   "connects",
                                                   Integer.toString(port),
                                                   "connections",
                                                   TimeUnit.SECONDS);
        this.disconnects = registry.newMeter(SocketConnector.class,
                                                   "disconnects",
                                                   Integer.toString(port),
                                                   "connections",
                                                   TimeUnit.SECONDS);
        this.connections = registry.newCounter(SocketConnector.class,
                                               "active-connections",
                                               Integer.toString(port));
    }

    @Override
    public void accept(int acceptorID) throws IOException, InterruptedException {
        super.accept(acceptorID);
        accepts.mark();
    }

    @Override
    protected void connectionOpened(Connection connection) {
        connections.inc();
        super.connectionOpened(connection);
        connects.mark();
    }

    @Override
    protected void connectionClosed(Connection connection) {
        super.connectionClosed(connection);
        disconnects.mark();
        final long duration = System.currentTimeMillis() - connection.getTimeStamp();
        this.duration.update(duration, TimeUnit.MILLISECONDS);
        connections.dec();
    }
}
