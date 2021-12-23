package com.sonic.agent.tests;

import com.sonic.agent.interfaces.PlatformType;
import com.sonic.agent.tests.android.AndroidTestTaskBootThread;
import com.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author JayWenStar
 * @date 2021/12/2 12:31 上午
 */
public class TaskManager {

    /**
     * key是boot的线程名，value是boot线程本身
     */
    private static ConcurrentHashMap<String, Thread> bootThreadsMap = new ConcurrentHashMap<>();


    /**
     * key是boot的线程名，value是boot线程启动的线程，因为是守护线程，所以当boot被停止后，child线程也会停止
     */
    private static ConcurrentHashMap<String, Set<Thread>> childThreadsMap = new ConcurrentHashMap<>();

    private static final Lock lock = new ReentrantLock();

    /**
     * 启动boot线程
     *
     * @param bootThread boot线程
     */
    public static void startBootThread(Thread bootThread) {
        bootThread.start();
        addBootThread(bootThread.getName(), bootThread);
    }

    /**
     * 启动boot线程（批量）
     *
     * @param bootThreads boot线程
     */
    public static void startBootThread(Thread... bootThreads) {
        for (Thread bootThread : bootThreads) {
            startBootThread(bootThread);
        }
    }

    /**
     * 启动子线程
     */
    public static void startChildThread(String key, Thread childThread) {
        childThread.start();
        addChildThread(key, childThread);
    }

    /**
     * 启动子线程（批量）
     */
    public static void startChildThread(String key, Thread... childThreads) {
        for (Thread childThread : childThreads) {
            startChildThread(key, childThread);
        }
    }


    /**
     * 添加boot线程
     *
     * @param key        用boot线程名作为key
     * @param bootThread boot线程
     */
    public static void addBootThread(String key, Thread bootThread) {
        clearTerminatedThread();
        bootThreadsMap.put(key, bootThread);
    }


    /**
     * 添加child线程（单个）
     *
     * @param key         用boot的线程名作为key
     * @param childThread 线程
     */
    public static void addChildThread(String key, Thread childThread) {
        clearTerminatedThread();
        lock.lock();
        if (childThreadsMap.containsKey(key)) {
            Set<Thread> threadsSet = childThreadsMap.get(key);
            if (CollectionUtils.isEmpty(threadsSet)) {
                threadsSet = new HashSet<>();
                threadsSet.add(childThread);
                childThreadsMap.put(key, threadsSet);
                lock.unlock();
                return;
            }
            threadsSet.add(childThread);
            lock.unlock();
            return;
        }
        Set<Thread> threadsSet = new HashSet<>();
        threadsSet.add(childThread);
        childThreadsMap.put(key, threadsSet);
        lock.unlock();
    }

    /**
     * 添加child线程（批量）
     *
     * @param key 用boot线程名作为key
     * @param set 线程set
     */
    public static void addChildThreadBatch(String key, HashSet<Thread> set) {
        clearTerminatedThread();
        lock.lock();
        if (childThreadsMap.containsKey(key)) {
            Set<Thread> threadsSet = childThreadsMap.get(key);
            if (CollectionUtils.isEmpty(threadsSet)) {
                childThreadsMap.put(key, set);
                lock.unlock();
                return;
            }
            childThreadsMap.get(key).addAll(set);
            lock.unlock();
            return;
        }
        childThreadsMap.put(key, set);
        lock.unlock();
    }

    /**
     * 清除已经结束的线程，如果boot线程已经结束，若对应child线程未结束，则强制停止child线程
     */
    public static void clearTerminatedThread() {
        // 过滤出已经结束的boot线程组
        Map<String, Thread> terminatedThread = bootThreadsMap.entrySet().stream()
                .filter(t -> !t.getValue().isAlive())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // 停止并删除boot线程
        terminatedThread.forEach((k, v) -> {
            v.interrupt();
            bootThreadsMap.remove(k);
        });
        // 删除boot衍生的线程
        terminatedThread.forEach((key, value) -> {
            childThreadsMap.remove(key);
        });
    }

    /**
     * 按照结果id、用例id、设备序列号强制停止手机正在执行的任务
     *
     * @param resultId 结果id
     * @param caseId   用例id
     * @param udId     设备序列号
     */
    public static void forceStopSuite(int platform, int resultId, int caseId, String udId) {
        String key = "";
        if (platform == PlatformType.ANDROID) {
            key = String.format(AndroidTestTaskBootThread.ANDROID_TEST_TASK_BOOT_PRE, resultId, caseId, udId);
        }
        if (platform == PlatformType.IOS) {
            key = String.format(IOSTestTaskBootThread.IOS_TEST_TASK_BOOT_PRE, resultId, caseId, udId);
        }
        // 停止boot线程
        Thread bootThread = bootThreadsMap.get(key);
        if (bootThread != null) {
            bootThread.interrupt();
        }
        // 清理map
        bootThreadsMap.remove(key);
        childThreadsMap.remove(key);
    }

}
