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
package org.cloud.sonic.agent.transport;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.cloud.sonic.agent.tools.SpringTool;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.FileInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * @author Eason
 * @date 2022/6/12 02:45
 */
@Slf4j
public class TransportConnectionThread implements Runnable {
    /**
     * second
     */
    public static final long DELAY = 10;

    public static final String THREAD_NAME = "transport-connection-thread";

    public static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    String serverHost = String.valueOf(SpringTool.getPropertiesValue("sonic.server.host"));
    Integer serverPort = Integer.valueOf(SpringTool.getPropertiesValue("sonic.server.port"));
    String serverCaPath = String.valueOf(SpringTool.getPropertiesValue("sonic.server.ca"));
    Boolean serverHttps = Boolean.valueOf(SpringTool.getPropertiesValue("sonic.server.https"));
    String key = String.valueOf(SpringTool.getPropertiesValue("sonic.agent.key"));

    @Override
    public void run() {
        Thread.currentThread().setName(THREAD_NAME);
        if (TransportWorker.client == null) {
            if (!TransportWorker.isKeyAuth) {
                return;
            }
            String url = String.format("ws://%s:%d/server/websockets/agent/%s", serverHost, serverPort, key)
                            .replace(":80/", "/")
                            .replace("ws://", serverHttps ? "wss://" : "ws://");

            URI uri = URI.create(url);
            TransportClient transportClient = new TransportClient(uri);

            if (serverHttps) {
                try {
                    log.info("Server Websocket: " + url);
                    log.info("Server Ca: " + serverCaPath);
                    InputStream is = new FileInputStream(serverCaPath);
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                    ks.load(null); // You don't need the KeyStore instance to come from a file.
                    ks.setCertificateEntry("caCert", caCert);
                    tmf.init(ks);

                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(null, tmf.getTrustManagers(), null);
                    transportClient.setSocketFactory(sslContext.getSocketFactory());
                }
                catch (Exception ex) {
                    StringWriter sw = new StringWriter();
                    ex.printStackTrace(new PrintWriter(sw));
                    log.error(sw.toString());
                }
            }
            transportClient.connect();
        } else {
            JSONObject ping = new JSONObject();
            ping.put("msg", "ping");
            TransportWorker.send(ping);
        }
    }
}
