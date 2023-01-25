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
package org.cloud.sonic.agent.automation;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.cloud.sonic.agent.tools.PortTool;

import javax.websocket.Session;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AndroidTouchHandler {
    private Map<Session, OutputStream> outputMap = new ConcurrentHashMap<>();
    private List<Session> NotStopSession = new ArrayList<>();
    private Map<Session, Thread> touchMap = new ConcurrentHashMap<>();

    public OutputStream getOutputStream(Session session){
        return outputMap.get(session);
    }

    public void startTouch(Session session, IDevice iDevice, String path) {
        Semaphore isTouchFinish = new Semaphore(0);

        Thread touchPro = new Thread(() -> {
            try {
                iDevice.executeShellCommand(String.format("CLASSPATH=%s exec app_process /system/bin org.cloud.sonic.android.plugin.SonicPluginTouchService", path)
                        , new IShellOutputReceiver() {
                            @Override
                            public void addOutput(byte[] bytes, int i, int i1) {
                                String res = new String(bytes, i, i1);
                                log.info(res);
                                if (res.contains("Address already in use")) {
                                    NotStopSession.add(session);
                                    isTouchFinish.release();
                                }
                                if (res.startsWith("starting")) {
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
                log.info("{} device touch service launch err"
                        , iDevice.getSerialNumber());
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
                outputMap.put(session, outputStream);
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
                NotStopSession.remove(session);
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
            outputMap.remove(session);
            AndroidDeviceBridgeTool.removeForward(iDevice, finalTouchPort, "sonictouchservice");
        });
        touchSocketThread.start();
        int w = 0;
        while (outputMap.get(session) == null) {
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
        touchMap.put(session, touchSocketThread);
    }

    public void stopTouch(Session session) {
        if (outputMap.get(session) != null) {
            try {
                outputMap.get(session).write("release \n".getBytes(StandardCharsets.UTF_8));
                outputMap.get(session).flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (touchMap.get(session) != null) {
            touchMap.get(session).interrupt();
            int wait = 0;
            while (!touchMap.get(session).isInterrupted()) {
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
        touchMap.remove(session);
    }
}
