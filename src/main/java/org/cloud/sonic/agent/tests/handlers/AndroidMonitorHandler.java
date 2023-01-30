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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AndroidMonitorHandler {
    private final Map<String, Thread> rotationMap = new ConcurrentHashMap<>();

    public interface IMonitorOutputReceiver {
        void output(String res);
    }

    public static class MonitorOutputReceiver implements IShellOutputReceiver {

        private final IDevice iDevice;
        private final IMonitorOutputReceiver receiver;

        public MonitorOutputReceiver(IDevice iDevice, IMonitorOutputReceiver receiver) {
            this.iDevice = iDevice;
            this.receiver = receiver;
        }

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            String res = new String(data, offset, length).replaceAll("\n", "").replaceAll("\r", "");
            if (res.length() > 0) {
                log.info(iDevice.getSerialNumber() + " rotation: " + res);
                AndroidDeviceManagerMap.getRotationMap().put(iDevice.getSerialNumber(), Integer.parseInt(res));
                receiver.output(res);
            }
        }

        @Override
        public void flush() {

        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    public boolean isMonitorRunning(IDevice iDevice) {
        return rotationMap.get(iDevice.getSerialNumber()) != null;
    }

    public void startMonitor(IDevice iDevice, IMonitorOutputReceiver receiver) {
        stopMonitor(iDevice);
        String path = AndroidDeviceBridgeTool.executeCommand(iDevice, "pm path org.cloud.sonic.android").trim()
                .replaceAll("package:", "")
                .replaceAll("\n", "")
                .replaceAll("\t", "");

        Thread rotationPro = new Thread(() -> {
            try {
                //开始启动
                iDevice.executeShellCommand(String.format("CLASSPATH=%s exec app_process /system/bin org.cloud.sonic.android.plugin.SonicPluginMonitorService", path)
                        , new MonitorOutputReceiver(iDevice, receiver), 0, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.info("{} rotation service stopped."
                        , iDevice.getSerialNumber());
                log.error(e.getMessage());
            }
        });
        rotationPro.start();
        rotationMap.put(iDevice.getSerialNumber(), rotationPro);
    }

    public void stopMonitor(IDevice iDevice) {
        if (rotationMap.get(iDevice.getSerialNumber()) != null) {
            rotationMap.get(iDevice.getSerialNumber()).interrupt();
        }
        rotationMap.remove(iDevice.getSerialNumber());
    }
}
