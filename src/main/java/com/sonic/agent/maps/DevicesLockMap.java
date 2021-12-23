package com.sonic.agent.maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 设备逻辑锁 Map
 *
 * @author JayWenStar
 * @date 2021/12/19 19:51 下午
 */
public class DevicesLockMap {

    private final static Logger logger = LoggerFactory.getLogger(DevicesLockMap.class);

    /**
     * key: udId（设备序列号）   value: Semaphore
     */
    private static Map<String, Semaphore> devicesLockMap = new ConcurrentHashMap<>();

    /**xxx
     * 用设备序列号上锁（无）
     *
     * @see #lockByUdId(String, Long, TimeUnit)
     */
    public static boolean lockByUdId(String udId) throws InterruptedException {
        return lockByUdId(udId, null, null);
    }

    /**
     * 用设备序列号上锁（可设置超时）
     *
     * @param udId       设备序列号
     * @param timeOut    获取锁超时时间    此参数必须和 timeUnit 同时存在
     * @param timeUnit   时间单位         此参数必须和 timeOut 同时存在
     * @return           true: 上锁成功   false: 上锁失败
     */
    public static boolean lockByUdId(String udId, Long timeOut, TimeUnit timeUnit) throws InterruptedException {
        // 校验参数
        Assert.hasText(udId, "udId must not be blank");
        if (timeOut != null || timeUnit != null) {
            Assert.isTrue(
                    timeOut != null && timeUnit != null,
                    "timeOut and timeUnit must not be null at the same time"
            );
        }
        Semaphore deviceLock;

        // 原子操作，避免put后被别的线程remove造成当前线程lock失效
        synchronized (WebSocketSessionMap.class) {
            Semaphore res = devicesLockMap.get(udId);
            if (res == null) {
                deviceLock = new Semaphore(1);
                devicesLockMap.put(udId, deviceLock);
            } else {
                deviceLock = res;
            }
        }

        // 无超时获取锁逻辑，直接锁并返回true
        if (timeOut == null) {
            deviceLock.acquire();
            return true;
        }

        // 如果有超时，则成功返回true，超时返回false
        try {
            return deviceLock.tryAcquire(timeOut, timeUnit);
        } catch (InterruptedException e) {
            logger.error("获取锁被中断，返回false");
            return false;
        }
    }

    /**
     * 解锁并从map中移除锁
     *
     * @param udId   设备序列号
     */
    public static void unlockAndRemoveByUdId(String udId) {
        Assert.hasText(udId, "udId must not be blank");
        Semaphore deviceLock;
        synchronized (WebSocketSessionMap.class) {
            deviceLock = devicesLockMap.get(udId);
            removeLockByUdId(udId);
        }
        if (deviceLock != null) {
            deviceLock.release();
        }
    }

    public static void removeLockByUdId(String udId) {
        devicesLockMap.remove(udId);
    }

}
