/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tests;

import org.cloud.sonic.agent.common.interfaces.PlatformType;
import org.cloud.sonic.agent.tests.android.AndroidTestTaskBootThread;
import org.cloud.sonic.agent.tests.common.RunStepThread;
import org.cloud.sonic.agent.tests.ios.IOSTestTaskBootThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.cloud.sonic.agent.tests.SuiteListener.runningTestsMap;

/**
 * @author JayWenStar
 * @date 2021/12/27 11:42 上午
 */
public class TaskManager {

    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    /**
     * key是boot的线程名，value是boot线程本身
     */
    private static ConcurrentHashMap<String, Thread> bootThreadsMap = new ConcurrentHashMap<>();

    /**
     * key是boot的线程名，value是boot线程启动的线程，因为是守护线程，所以当boot被停止后，child线程也会停止
     */
    private static ConcurrentHashMap<String, Set<Thread>> childThreadsMap = new ConcurrentHashMap<>();

    /**
     * 记录正在运行的rid记录
     */
    private static Set<Integer> runningRidSet =  Collections.synchronizedSet(new HashSet<>());

    /**
     * 记录正在运行的udId记录
     */
    private static Set<String> runningUdIdSet =  Collections.synchronizedSet(new HashSet<>());

    private static final Lock lock = new ReentrantLock();

    public static boolean ridRunning(Integer rid) {
        return runningRidSet.contains(rid);
    }

    public static boolean udIdRunning(String udId) {
        return runningUdIdSet.contains(udId);
    }

    /**
     * 启动boot线程
     *
     * @param bootThread boot线程
     */
    public static void startBootThread(Thread bootThread) {
        bootThread.start();
        addBootThread(bootThread.getName(), bootThread);

        String[] split = bootThread.getName().split("-");
        runningRidSet.add(Integer.parseInt(split[split.length-3]));
        runningUdIdSet.add(split[split.length-1]);
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
     * 根据key清除线程（非停止）
     *
     * @param key 线程名
     */
    public static void clearTerminatedThreadByKey(String key) {
        bootThreadsMap.remove(key);
        Set<Thread> threads = childThreadsMap.get(key);
        if (threads == null) {
            return;
        }
        for (Thread thread : threads) {
            thread.interrupt();
        }
        childThreadsMap.remove(key);

        String[] split = key.split("-");
        runningRidSet.remove(Integer.parseInt(split[split.length-3]));
        runningUdIdSet.remove(split[split.length-1]);
    }

    /**
     * 清除已经结束的线程，如果boot线程已经结束，若对应child线程未结束，则强制停止child线程
     */
    public static void clearTerminatedThread() {
        logger.debug("clearTerminatedThread");
        // 过滤出已经结束的boot线程组
        Map<String, Thread> terminatedThread = bootThreadsMap.entrySet().stream()
                .filter(t -> !t.getValue().isAlive())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // 停止并删除boot线程
        terminatedThread.forEach((k, v) -> {
            v.interrupt();
            bootThreadsMap.remove(k);

            String[] split = k.split("-");
            runningRidSet.remove(Integer.parseInt(split[split.length-3]));
            runningUdIdSet.remove(split[split.length-1]);
        });
        // 删除boot衍生的线程
        terminatedThread.forEach((key, value) -> {
            childThreadsMap.remove(key);
            String[] split = key.split("-");
            runningRidSet.remove(Integer.parseInt(split[split.length-3]));
            runningUdIdSet.remove(split[split.length-1]);
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
        Set<Thread> removed = childThreadsMap.remove(key);
        for (Thread thread : removed) {
            if (thread instanceof RunStepThread) {
                ((RunStepThread) thread).setStopped(true);
            }
        }
        runningTestsMap.remove(resultId + "");

        runningRidSet.remove(resultId);
        runningUdIdSet.remove(udId);
    }

    /**
     * 停止子线程
     * 不能使用 {@link Thread#stop()} 、{@link Thread#interrupt()} ，
     * 因为目前的websocket会用当前所属线程做一些事，强制停止会导致一些问题
     *
     * @param key  子线程key
     */
    public static void forceStopDebugStepThread(String key) {
        Set<Thread> threads = childThreadsMap.get(key);
        if (threads == null) {
            return;
        }
        for (Thread thread : threads) {
            RunStepThread runStepThread = (RunStepThread) thread;
            runStepThread.setStopped(true);
        }
        childThreadsMap.remove(key);
    }

}
