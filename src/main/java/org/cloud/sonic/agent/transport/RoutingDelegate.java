package org.cloud.sonic.agent.transport;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

/**
 * see https://www.cnblogs.com/xiaoqi/p/spring-boot-route.html
 */
@Service
public class RoutingDelegate {

    public ResponseEntity<String> redirect(HttpServletRequest request, HttpServletResponse response, String routeUrl, String prefix) {
        try {
            String redirectUrl = createPredictUrl(request, routeUrl, prefix);
            RequestEntity requestEntity = createRequestEntity(request, redirectUrl);
            return route(requestEntity);
        } catch (Exception e) {
            if (e.getMessage().contains("{") && e.getMessage().contains("}")) {
                return new ResponseEntity(JSON.parseObject(e.getMessage().substring(e.getMessage().indexOf("{"), e.getMessage().lastIndexOf("}") + 1)).toJSONString(), HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                JSONObject err = new JSONObject();
                err.put("error", "-1");
                err.put("message", "REDIRECT ERROR");
                err.put("traceback", "REDIRECT ERROR");
                JSONObject base = new JSONObject();
                base.put("value", err);
                base.put("sessionId", "");
                return new ResponseEntity(base.toJSONString(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private String createPredictUrl(HttpServletRequest request, String routeUrl, String prefix) {
        String queryString = request.getQueryString();
        return routeUrl + request.getRequestURI().replace(prefix, "") +
                (queryString != null ? "?" + queryString : "");
    }


    private RequestEntity createRequestEntity(HttpServletRequest request, String url) throws URISyntaxException, IOException {
        String method = request.getMethod();
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        MultiValueMap<String, String> headers = parseRequestHeader(request);
        byte[] body = parseRequestBody(request);
        return new RequestEntity<>(body, headers, httpMethod, new URI(url));
    }

    private ResponseEntity<String> route(RequestEntity requestEntity) {
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.exchange(requestEntity, String.class);
    }


    private byte[] parseRequestBody(HttpServletRequest request) throws IOException {
        InputStream inputStream = request.getInputStream();
        return StreamUtils.copyToByteArray(inputStream);
    }

    private MultiValueMap<String, String> parseRequestHeader(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        List<String> headerNames = Collections.list(request.getHeaderNames());
        for (String headerName : headerNames) {
            List<String> headerValues = Collections.list(request.getHeaders(headerName));
            for (String headerValue : headerValues) {
                headers.add(headerName, headerValue);
            }
        }
        return headers;
    }
}
