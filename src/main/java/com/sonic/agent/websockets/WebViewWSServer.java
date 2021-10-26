package com.sonic.agent.websockets;

import com.android.ddmlib.IDevice;
import com.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.springframework.stereotype.Component;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author ZhouYiXun
 * @des
 * @date 2021/10/25 23:03
 */
@Component
@ServerEndpoint(value = "/websockets/webView", configurator = MyEndpointConfigure.class)
public class WebViewWSServer {

    @OnOpen
    public void onOpen(Session session) throws Exception {
        IDevice iDevice = AndroidDeviceBridgeTool.getIDeviceByUdId("MDX0220610012002");
        String webview = AndroidDeviceBridgeTool
                .executeCommand(iDevice,"cat /proc/net/unix | grep webview");
        String name = webview.substring(webview.indexOf("@"));
        System.out.println(name);
        AndroidDeviceBridgeTool.forward(iDevice, 8888, name);
        Socket touchSocket = null;
        OutputStream outputStream = null;
        try {
            touchSocket = new Socket("localhost", 8888);
            outputStream = touchSocket.getOutputStream();
        }catch (Exception e){

        }
    }
}
