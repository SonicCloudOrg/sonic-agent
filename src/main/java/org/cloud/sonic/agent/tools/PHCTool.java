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
package org.cloud.sonic.agent.tools;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class PHCTool {
    private static String baseUrl = "http://127.0.0.1:7531";
    private static RestTemplate restTemplate;

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        PHCTool.restTemplate = restTemplate;
    }

    public static void setPosition(int position, String type) {
        if (!isSupport()) return;
        log.info("set hub position: {} {}", position, type);
        JSONObject re = new JSONObject();
        re.put("position", position);
        re.put("type", type);
        restTemplate.postForEntity(baseUrl + "/control", re, String.class);
    }

    public static boolean isSupport() {
        try {
            ResponseEntity<String> responseEntity =
                    restTemplate.getForEntity(baseUrl + "/ping", String.class);
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                if ("pong".equals(responseEntity.getBody())) {
                    log.info("hub is ready.");
                    return true;
                }
            }
            log.info("hub is not ready.");
            return false;
        }catch (Exception e){
            log.info("hub is not ready. ignore...");
            return false;
        }
    }
}
