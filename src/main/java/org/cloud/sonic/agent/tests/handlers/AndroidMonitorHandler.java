package org.cloud.sonic.agent.tests.handlers;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AndroidMonitorHandler {
    private Map<String, Thread> rotationMap = new ConcurrentHashMap<>();

    public interface IMonitorOutputReceiver {
        void output(String res);
    }

    class MonitorOutputReceiver implements IShellOutputReceiver {

        private IDevice iDevice;
        private IMonitorOutputReceiver receiver;

        public MonitorOutputReceiver(IDevice iDevice, IMonitorOutputReceiver receiver) {
            this.iDevice = iDevice;
            this.receiver = receiver;
        }

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            String res = new String(data, offset, length).replaceAll("\n", "").replaceAll("\r", "");
            log.info(iDevice.getSerialNumber() + " rotation: " + res);
            receiver.output(res);
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
