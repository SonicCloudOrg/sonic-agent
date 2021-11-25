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
    private static LinkedBlockingQueue<JSONObject> dataQueue;
    public static ExecutorService cachedThreadPool;
    public static boolean isPassSecurity = false;
    private static Future<?> read = null;

    @Bean
    public void nettyMsgInit() {
        cachedThreadPool = Executors.newCachedThreadPool();
        dataQueue = new LinkedBlockingQueue<>();
    }

    public static void send(JSONObject jsonObject) {
        dataQueue.offer(jsonObject);
    }

    public static void readQueue() {
        if (read != null) {
            isPassSecurity = false;
            while (!read.isDone()) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        isPassSecurity = true;
        read = cachedThreadPool.submit(() -> {
            while (isPassSecurity) {
                try {
                    if (NettyClientHandler.channel != null) {
                        if (!dataQueue.isEmpty()) {
                            JSONObject m = dataQueue.poll();
                            m.put("agentId", AgentTool.agentId);
                            NettyClientHandler.channel.writeAndFlush(m.toJSONString());
                        }else{
                            Thread.sleep(1000);
                        }
                    } else {
                        Thread.sleep(10000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
