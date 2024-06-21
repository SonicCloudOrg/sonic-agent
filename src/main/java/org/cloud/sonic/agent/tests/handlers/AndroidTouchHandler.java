/*
 *   sonic-agent  Agent of Sonic Cloud Real Machine Platform.
 *   Copyright (C) 2022 SonicCloudOrg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published
 *   by the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.cloud.sonic.agent.tests.handlers;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.common.enums.AndroidKey;
import org.cloud.sonic.agent.common.maps.AndroidDeviceManagerMap;
import org.cloud.sonic.agent.common.maps.HandlerMap;
import org.cloud.sonic.agent.tools.PortTool;
import org.cloud.sonic.driver.common.tool.SonicRespException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

@Slf4j
public class AndroidTouchHandler {
    private static final Map<String, OutputStream> outputMap = new ConcurrentHashMap<>();
    private static final Map<String, Thread> touchMap = new ConcurrentHashMap<>();
    private static final Map<String, TouchMode> touchModeMap = new ConcurrentHashMap<>();
    private static final Map<String, int[]> sizeMap = new ConcurrentHashMap<>();
    // 默认的滑动操作完成时间，单位为毫秒
    private static final int DEFAULT_SWIPE_DURATION = 500;

    public enum TouchMode {
        SONIC_APK,
        ADB,
        APPIUM_UIAUTOMATOR2_SERVER;
    }

    public static void switchTouchMode(IDevice iDevice, TouchMode mode) {
        touchModeMap.put(iDevice.getSerialNumber(), mode);
    }

    public static TouchMode getTouchMode(IDevice iDevice) {
        return touchModeMap.get(iDevice.getSerialNumber()) == null ? TouchMode.ADB : touchModeMap.get(iDevice.getSerialNumber());
    }

    public static void tap(IDevice iDevice, int x, int y) throws SonicRespException {
        switch (getTouchMode(iDevice)) {
            case SONIC_APK -> {
                int[] re = transferWithRotation(iDevice, x, y);
                writeToOutputStream(iDevice, String.format("down %d %d\n", re[0], re[1]));
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeToOutputStream(iDevice, "up\n");
            }
            case ADB -> AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", x, y));
            case APPIUM_UIAUTOMATOR2_SERVER -> {
                AndroidStepHandler curStepHandler = HandlerMap.getAndroidMap().get(iDevice.getSerialNumber());
                if (curStepHandler != null && curStepHandler.getAndroidDriver() != null) {
                    curStepHandler.getAndroidDriver().tap(x, y);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + getTouchMode(iDevice));
        }
    }

    public static void longPress(IDevice iDevice, int x, int y, int time) throws SonicRespException {
        switch (getTouchMode(iDevice)) {
            case SONIC_APK -> {
                int[] re = transferWithRotation(iDevice, x, y);
                writeToOutputStream(iDevice, String.format("down %d %d\n", re[0], re[1]));
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeToOutputStream(iDevice, "up\n");
            }
            case ADB -> AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x, y, x, y, time));
            case APPIUM_UIAUTOMATOR2_SERVER -> {
                AndroidStepHandler curStepHandler = HandlerMap.getAndroidMap().get(iDevice.getSerialNumber());
                if (curStepHandler != null && curStepHandler.getAndroidDriver() != null) {
                    curStepHandler.getAndroidDriver().longPress(x, y, time);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + getTouchMode(iDevice));
        }
    }

    public static void swipe(IDevice iDevice, int x1, int y1, int x2, int y2) throws SonicRespException {
        swipe(iDevice, x1, y1, x2, y2, DEFAULT_SWIPE_DURATION);
    }

    public static void swipe(IDevice iDevice, int x1, int y1, int x2, int y2, int swipeDuration) throws SonicRespException {
        switch (getTouchMode(iDevice)) {
            case SONIC_APK -> {
                int[] re1 = transferWithRotation(iDevice, x1, y1);
                int[] re2 = transferWithRotation(iDevice, x2, y2);
                writeToOutputStream(iDevice, String.format("down %d %d\n", re1[0], re1[1]));
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long startTime = System.currentTimeMillis();
                while (true) {
                    // 当前时间
                    long currentTime = System.currentTimeMillis();
                    // 计算时间进度
                    float timeProgress = (currentTime - startTime) / (float) swipeDuration;
                    if (timeProgress >= 1.0f) {
                        // 已经过渡到结束值，停止过渡
                        writeToOutputStream(iDevice, String.format("move %d %d\n", re2[0], re2[1]));
                        break;
                    }
                    BiFunction<Integer, Integer, Integer> transitionX = (start, end) ->
                            (int) (start + (end - start) * timeProgress);
                    BiFunction<Integer, Integer, Integer> transitionY = (start, end) ->
                            (int) (start + (end - start) * timeProgress);

                    int currentX = transitionX.apply(re1[0], re2[0]);
                    int currentY = transitionY.apply(re1[1], re2[1]);
                    // 使用当前坐标进行操作
                    writeToOutputStream(iDevice, String.format("move %d %d\n", currentX, currentY));
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeToOutputStream(iDevice, "up\n");
            }
            case ADB -> AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d",
                    x1, y1, x2, y2, swipeDuration));
            case APPIUM_UIAUTOMATOR2_SERVER -> {
                AndroidStepHandler curStepHandler = HandlerMap.getAndroidMap().get(iDevice.getSerialNumber());
                if (curStepHandler != null && curStepHandler.getAndroidDriver() != null) {
                    curStepHandler.getAndroidDriver().swipe(x1, y1, x2, y2, swipeDuration);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + getTouchMode(iDevice));
        }
    }

    public static void drag(IDevice iDevice, int x1, int y1, int x2, int y2) throws SonicRespException {
        drag(iDevice, x1, y1, x2, y2, DEFAULT_SWIPE_DURATION);
    }

    public static void drag(IDevice iDevice, int x1, int y1, int x2, int y2, int swipeDuration) throws SonicRespException {
        switch (getTouchMode(iDevice)) {
            case ADB ->
                    AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input draganddrop %d %d %d %d %d", x1, y1, x2, y2, swipeDuration));
            case APPIUM_UIAUTOMATOR2_SERVER -> {
                AndroidStepHandler curStepHandler = HandlerMap.getAndroidMap().get(iDevice.getSerialNumber());
                if (curStepHandler != null && curStepHandler.getAndroidDriver() != null) {
                    curStepHandler.getAndroidDriver().drag(x1, y1, x2, y2, swipeDuration, null, null);
                }
            }
            case SONIC_APK -> {
                // 原本这段代码是在`swipe(IDevice iDevice, int x1, int y1, int x2, int y2, int swipeDuration)`中的
                // 但是swipe的效果应该是在屏幕滑动，而不是在第一个坐标按下时存在延迟，所以放在drag方法里面更加符合
                int[] re1 = transferWithRotation(iDevice, x1, y1);
                int[] re2 = transferWithRotation(iDevice, x2, y2);
                writeToOutputStream(iDevice, String.format("down %d %d\n", re1[0], re1[1]));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long startTime = System.currentTimeMillis();
                while (true) {
                    // 当前时间
                    long currentTime = System.currentTimeMillis();
                    // 计算时间进度
                    float timeProgress = (currentTime - startTime) / (float) swipeDuration;
                    if (timeProgress >= 1.0f) {
                        // 已经过渡到结束值，停止过渡
                        writeToOutputStream(iDevice, String.format("move %d %d\n", re2[0], re2[1]));
                        break;
                    }
                    BiFunction<Integer, Integer, Integer> transitionX = (start, end) ->
                            (int) (start + (end - start) * timeProgress);
                    BiFunction<Integer, Integer, Integer> transitionY = (start, end) ->
                            (int) (start + (end - start) * timeProgress);

                    int currentX = transitionX.apply(re1[0], re2[0]);
                    int currentY = transitionY.apply(re1[1], re2[1]);
                    // 使用当前坐标进行操作
                    writeToOutputStream(iDevice, String.format("move %d %d\n", currentX, currentY));
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeToOutputStream(iDevice, "up\n");
            }

        }
    }

    public static void motionEvent(IDevice iDevice, String motionEventType, int x1, int y1) throws SonicRespException {
        switch (getTouchMode(iDevice)) {
            case ADB ->
                    AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input motionevent %s %d %d", motionEventType, x1, y1));
            case SONIC_APK -> {
                int[] re1 = transferWithRotation(iDevice, x1, y1);
                writeToOutputStream(iDevice, String.format("%s %d %d\n", motionEventType.toLowerCase(), re1[0], re1[1]));
            }
            case APPIUM_UIAUTOMATOR2_SERVER -> {
                AndroidStepHandler curStepHandler = HandlerMap.getAndroidMap().get(iDevice.getSerialNumber());
                if (curStepHandler != null && curStepHandler.getAndroidDriver() != null) {
                    curStepHandler.getAndroidDriver().touchAction(motionEventType.toLowerCase(), x1, y1);
                }
            }
        }
    }

    private static int[] transferWithRotation(IDevice iDevice, int x, int y) {
        Integer directionStatus = AndroidDeviceManagerMap.getRotationMap().get(iDevice.getSerialNumber());
        if (directionStatus == null) {
            directionStatus = 0;
        }
        int _x;
        int _y;
        int width;
        int height;
        if (sizeMap.get(iDevice.getSerialNumber()) != null) {
            width = sizeMap.get(iDevice.getSerialNumber())[0];
            height = sizeMap.get(iDevice.getSerialNumber())[1];
        } else {
            String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
            width = Integer.parseInt(size.split("x")[0]);
            height = Integer.parseInt(size.split("x")[1]);
        }
        if (directionStatus == 1 || directionStatus == 3) {
            _x = directionStatus == 1 ? width - y : y - width * 3;
            _y = directionStatus == 1 ? x : -x;
        } else {
            _x = directionStatus == 2 ? width - x : x;
            _y = directionStatus == 2 ? height - y : y;
        }
        return new int[]{_x, _y};
    }

    public static void writeToOutputStream(IDevice iDevice, String msg) {
        OutputStream outputStream = outputMap.get(iDevice.getSerialNumber());
        if (outputStream != null) {
            try {
                outputStream.write(msg.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                log.info("write to apk failed cause by: {}, auto switch to adb touch mode...", e.getMessage());
                switchTouchMode(iDevice, TouchMode.ADB);
            }
        } else {
            log.info("{} write output stream is null.", iDevice.getSerialNumber());
        }
    }

    public static void startTouch(IDevice iDevice) {
        stopTouch(iDevice);
        String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
        sizeMap.put(iDevice.getSerialNumber(), Arrays.stream(size.split("x")).mapToInt(Integer::parseInt).toArray());
        if (AndroidDeviceBridgeTool.getOrientation(iDevice) != 0) {
            AndroidDeviceBridgeTool.pressKey(iDevice, AndroidKey.HOME);
        }
        String path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                .replaceAll("package:", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "");

        Semaphore isTouchFinish = new Semaphore(0);
        String udId = iDevice.getSerialNumber();

        Thread touchPro = new Thread(() -> {
            try {
                iDevice.executeShellCommand(String.format("CLASSPATH=%s exec app_process /system/bin org.cloud.sonic.android.plugin.SonicPluginTouchService", path)
                        , new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                log.info(res);
                                if (res.contains("Address already in use") || res.startsWith("starting")) {
                                    isTouchFinish.release();
                                }
                            }

                            @Override
                            public void flush() {
                            }

                            @Override
                            public boolean isCancelled() {
                                return false;
                            }
                        }, 0, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.info("{} device touch service launch err", udId);
                log.error(e.getMessage());
            }
        });
        touchPro.start();

        int finalTouchPort = PortTool.getPort();
        Thread touchSocketThread = new Thread(() -> {
            int wait = 0;
            while (!isTouchFinish.tryAcquire()) {
                wait++;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (wait > 20) {
                    return;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
            }
            AndroidDeviceBridgeTool.forward(iDevice, finalTouchPort, "sonictouchservice");
            Socket touchSocket = null;
            OutputStream outputStream = null;
            try {
                touchSocket = new Socket("localhost", finalTouchPort);
                outputStream = touchSocket.getOutputStream();
                outputMap.put(udId, outputStream);
                while (touchSocket.isConnected() && !Thread.interrupted()) {
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                log.info("error: {}", e.getMessage());
            } finally {
                if (touchPro.isAlive()) {
                    touchPro.interrupt();
                    log.info("touch thread closed.");
                }
                if (touchSocket != null && touchSocket.isConnected()) {
                    try {
                        touchSocket.close();
                        log.info("touch socket closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (outputStream != null) {
                    try {
                        outputStream.close();
                        log.info("touch output stream closed.");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            outputMap.remove(udId);
            AndroidDeviceBridgeTool.removeForward(iDevice, finalTouchPort, "sonictouchservice");
            sizeMap.remove(udId);
        });
        touchSocketThread.start();
        int w = 0;
        while (outputMap.get(udId) == null) {
            if (w > 10) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            w++;
        }
        touchMap.put(udId, touchSocketThread);
    }

    public static void stopTouch(IDevice iDevice) {
        String udId = iDevice.getSerialNumber();
        if (outputMap.get(udId) != null) {
            try {
                outputMap.get(udId).write("release \n".getBytes(StandardCharsets.UTF_8));
                outputMap.get(udId).flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (touchMap.get(udId) != null) {
            touchMap.get(udId).interrupt();
            int wait = 0;
            while (!touchMap.get(udId).isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                wait++;
                if (wait >= 3) {
                    break;
                }
            }
        }
        touchModeMap.remove(udId);
        touchMap.remove(udId);
    }
}
