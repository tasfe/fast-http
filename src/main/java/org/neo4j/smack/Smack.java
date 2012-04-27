package org.neo4j.smack;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.smack.event.RequestEvent;
import org.neo4j.smack.handler.CombinedHandler;
import org.neo4j.smack.handler.DatabaseWorkDivider;
import org.neo4j.smack.handler.DefaultExceptionHandler;
import org.neo4j.smack.handler.DeserializationHandler;
import org.neo4j.smack.handler.RoutingHandler;
import org.neo4j.smack.handler.TransactionPreparationHandler;
import org.neo4j.smack.http.NettyHttpPipelineFactory;
import org.neo4j.smack.routing.Endpoint;
import org.neo4j.smack.routing.Router;
import org.neo4j.smack.routing.RoutingDefinition;

public class Smack {

    private int port;
    private String host;
    private Router router = new Router();
    private ServerBootstrap netty;
    
    private InputPipeline inputPipeline;
    
    private ServerSocketChannelFactory channelFactory;
    private ChannelGroup openChannels = new DefaultChannelGroup("SmackServer");
    private GraphDatabaseService database;
    private DatabaseWorkDivider workDivider;
    private DefaultExceptionHandler exceptionHandler;

    public Smack(String host, int port, GraphDatabaseService db) {
        this.host = host;
        this.port = port;
        this.database = db;
    }
    
    @SuppressWarnings("unchecked")
    public void start() {
        
        router.compileRoutes();
        
        // MAIN PIPELINE

        exceptionHandler = new DefaultExceptionHandler();
        workDivider = new DatabaseWorkDivider(database, exceptionHandler);

        inputPipeline = new InputPipeline(exceptionHandler);
        
        inputPipeline.addHandler(new CombinedHandler<RequestEvent>(
                new RoutingHandler(router), 
                new DeserializationHandler()));
        inputPipeline.addHandler(new CombinedHandler<RequestEvent>(
                new TransactionPreparationHandler(), 
                workDivider));
        
        inputPipeline.start();

        // NETTY 
        
        channelFactory = 
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(new DaemonThreadFactory("SocketMaster")),
                    Executors.newCachedThreadPool(new DaemonThreadFactory("SocketSlave")));
        netty = new ServerBootstrap(channelFactory);

        // Set up the event pipeline factory.
        netty.setPipelineFactory(new NettyHttpPipelineFactory(inputPipeline, openChannels));

        // Bind and start to accept incoming connections.
        openChannels.add(netty.bind(new InetSocketAddress(host, port)));

    }
    
    public void stop() {
        if (openChannels!=null) openChannels.close().awaitUninterruptibly();
        if (channelFactory!=null) channelFactory.releaseExternalResources();
        if (workDivider!=null) workDivider.stop();
        if (inputPipeline!=null) inputPipeline.stop();
    }
    
    public void addRoute(String route, RoutingDefinition target) {
        router.addRoute(route, target);
    }
    
    public void addRoute(String route, Endpoint target) {
        router.addRoute(route, target);
    }
    
    public void addRoute(String route, Object target) {
        router.addRoute(route, target);
    }
    
    public GraphDatabaseService getDatabase() {
        return database;
    }
    
}