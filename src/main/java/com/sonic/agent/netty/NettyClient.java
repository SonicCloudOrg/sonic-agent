package com.sonic.agent.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class NettyClient implements ApplicationRunner {
    private final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    private NioEventLoopGroup group = new NioEventLoopGroup(4);
    private Channel channel;
    private Bootstrap bootstrap;
    @Value("${sonic.server.transport-port}")
    private int serverPort;
    @Value("${sonic.server.host}")
    private String serverHost;
    @Value("${sonic.agent.port}")
    private int agentPort;
    @Value("${sonic.agent.host}")
    private String agentHost;
    @Value("${sonic.agent.key}")
    private String key;
    @Value("${spring.version}")
    private String version;

    @Override
    public void run(ApplicationArguments args) {
        // todo 根据 https://netty.io/wiki/native-transports.html 可针对不同系统进行优化，server端的netty同理
        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(group)
                .option(ChannelOption.TCP_NODELAY, true)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        socketChannel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingDecoder());
                        socketChannel.pipeline().addLast(MarshallingCodeCFactory.buildMarshallingEncoder());
                        socketChannel.pipeline().addLast(new SecurityHandler(NettyClient.this, key, agentHost, agentPort, version));
                    }
                });
        doConnect();
    }

    protected void doConnect() {
        if (channel != null && channel.isActive()) {
            return;
        }
        ChannelFuture future = bootstrap.connect(serverHost, serverPort);
        future.addListener((ChannelFutureListener) futureListener -> {
            if (futureListener.isSuccess()) {
                channel = futureListener.channel();
            } else {
                logger.info("连接到服务器{}:{}失败！10s后重连...", serverHost, serverPort);
                futureListener.channel().eventLoop().schedule(() -> doConnect(), 10, TimeUnit.SECONDS);
            }
        });
    }
}