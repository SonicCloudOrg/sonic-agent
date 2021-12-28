package com.sonic.agent.netty;

import com.alibaba.fastjson.JSONObject;
import com.sonic.agent.tools.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
public class NettyThreadPool {
    private final Logger logger = LoggerFactory.getLogger(NettyThreadPool.class);
    private static MsgDispatcher msgDispatcher;
    public static ExecutorService cachedThreadPool;

    @Bean
    public void nettyMsgInit() {
        cachedThreadPool = Executors.newCachedThreadPool();
        msgDispatcher = new MsgDispatcher();
        msgDispatcher.start();
    }

    public static void send(JSONObject jsonObject) {
        msgDispatcher.send(jsonObject);
    }
}
