package com.sonic.agent.rabbitmq;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class RabbitMQThread {
    private static LinkedBlockingQueue<JSONObject> msgQueue;
    public static ExecutorService cachedThreadPool;
    public static boolean isPass = false;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Bean
    public void rabbitMsgInit() {
        cachedThreadPool = Executors.newCachedThreadPool();
        msgQueue = new LinkedBlockingQueue<>();
    }

    public static void send(JSONObject jsonObject) {
        msgQueue.offer(jsonObject);
    }

    @Bean
    public void sendToRabbitMQ() {
        cachedThreadPool.submit(() -> {
            while (true) {
                if (!isPass) {
                    Thread.sleep(5000);
                    continue;
                }
                try {
                    if (!msgQueue.isEmpty()) {
                        rabbitTemplate.convertAndSend("DataExchange", "data", msgQueue.poll());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
