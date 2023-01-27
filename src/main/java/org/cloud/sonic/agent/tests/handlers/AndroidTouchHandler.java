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
import org.cloud.sonic.agent.common.maps.AndroidDeviceManagerMap;
import org.cloud.sonic.agent.tools.PortTool;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AndroidTouchHandler {
    private final Map<String, OutputStream> outputMap = new ConcurrentHashMap<>();
    private final Map<String, Thread> touchMap = new ConcurrentHashMap<>();
    private int touchMode = TouchMode.SONIC_APK;

    private int width;
    private int height;

    public interface TouchMode {
        int SONIC_APK = 1;
        int ADB = 2;
        int APPIUM_SERVER = 3;
    }

    public void switchTouchMode(int mode) {
        touchMode = mode;
    }

    public void tap(IDevice iDevice, int x, int y) {
        switch (touchMode) {
            case TouchMode.SONIC_APK -> {
                int[] re = transferWithRotation(iDevice, x, y);
                writeToOutputStream(iDevice, String.format("down %d %d\n", re[0], re[1]));
                writeToOutputStream(iDevice, "up\n");
            }
            case TouchMode.ADB ->
                    AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input tap %d %d", x, y));
            default -> throw new IllegalStateException("Unexpected value: " + touchMode);
        }
    }

    public void longPress(IDevice iDevice, int x, int y, int time) {
        switch (touchMode) {
            case TouchMode.SONIC_APK -> {
                writeToOutputStream(iDevice, String.format("down %d %d\n", x, y));
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                writeToOutputStream(iDevice, "up\n");
            }
            case TouchMode.ADB ->
                    AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x, y, x, y, time));
            default -> throw new IllegalStateException("Unexpected value: " + touchMode);
        }
    }

    public void swipe(IDevice iDevice, int x1, int y1, int x2, int y2) {
        switch (touchMode) {
            case TouchMode.SONIC_APK -> {
                writeToOutputStream(iDevice, String.format("down %d %d\n", x1, y1));
                writeToOutputStream(iDevice, String.format("move %d %d\n", x2, y2));
                writeToOutputStream(iDevice, "up\n");
            }
            case TouchMode.ADB ->
                    AndroidDeviceBridgeTool.executeCommand(iDevice, String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, 300));
            default -> throw new IllegalStateException("Unexpected value: " + touchMode);
        }
    }

    private int[] transferWithRotation(IDevice iDevice, int x, int y) {
        int directionStatus = AndroidDeviceManagerMap.getRotationMap().get(iDevice.getSerialNumber());
        if (directionStatus != 0 && directionStatus != 2) {
            x = directionStatus == 1 ? width - x : x - width * 3;
            y = directionStatus == 1 ? y : -y;
        } else {
            x = directionStatus == 2 ? width - x : x;
            y = directionStatus == 2 ? height - y : y;
        }
        return new int[]{x, y};
    }

    public void writeToOutputStream(IDevice iDevice, String msg) {
        OutputStream outputStream = outputMap.get(iDevice.getSerialNumber());
        if (outputStream != null) {
            try {
                outputStream.write(msg.getBytes());
                outputStream.flush();
            } catch (IOException e) {
                log.info("write to apk failed cause by: {}, auto switch to adb touch mode...", e.getMessage());
                switchTouchMode(TouchMode.ADB);
            }
        }
    }

    public void startTouch(IDevice iDevice) {
        stopTouch(iDevice);
        String size = AndroidDeviceBridgeTool.getScreenSize(iDevice);
        width = Integer.parseInt(size.split("x")[0]);
        height = Integer.parseInt(size.split("x")[1]);
        if (AndroidDeviceBridgeTool.getOrientation(iDevice) != 0) {
            AndroidDeviceBridgeTool.pressKey(iDevice, 3);
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

    public void stopTouch(IDevice iDevice) {
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
        touchMap.remove(udId);
    }
}
