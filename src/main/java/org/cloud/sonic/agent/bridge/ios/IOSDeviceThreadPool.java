package org.cloud.sonic.agent.bridge.ios;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * @author ZhouYiXun
 * @des 所有iOS相关线程都放这个线程池
 * @date 2021/08/25 19:26
 */
@Configuration
public class IOSDeviceThreadPool {
    public static ExecutorService cachedThreadPool;

    @Bean
    public ExecutorService iOSThreadPoolInit() {
        cachedThreadPool = Executors.newCachedThreadPool();
        return cachedThreadPool;
    }

    @PreDestroy
    public void shutdownThreadPool() {
        if (cachedThreadPool != null) {
            cachedThreadPool.shutdown();
        }
    }
}