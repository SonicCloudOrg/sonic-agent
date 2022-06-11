package org.cloud.sonic.agent.transport;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.tools.BytesTool;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Configuration
@Slf4j
public class TransportWorker {
    private static LinkedBlockingQueue<JSONObject> dataQueue = new LinkedBlockingQueue<>();
    public static ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    public static TransportClient client = null;
    public static Boolean isKeyAuth = true;

    public static void send(JSONObject jsonObject) {
        dataQueue.offer(jsonObject);
    }

    public static void readQueue() {
        cachedThreadPool.execute(() -> {
            while (client != null) {
                try {
                    if (client.isOpen()) {
                        if (!dataQueue.isEmpty()) {
                            JSONObject m = dataQueue.poll();
                            m.put("agentId", BytesTool.agentId);
                            client.send(m.toJSONString());
                        } else {
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
