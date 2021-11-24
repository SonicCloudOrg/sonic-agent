package com.sonic.agent.websockets;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

@Service
public class RoutingDelegate {
 
 public ResponseEntity<String> redirect(HttpServletRequest request, HttpServletResponse response, String routeUrl, String prefix) {
  try {
   // build up the redirect URL
   String redirectUrl = createRedictUrl(request,routeUrl, prefix);
   RequestEntity requestEntity = createRequestEntity(request, redirectUrl);
   return route(requestEntity);
  } catch (Exception e) {
   return new ResponseEntity("REDIRECT ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
  }
 }
 
 private String createRedictUrl(HttpServletRequest request, String routeUrl, String prefix) {
  String queryString = request.getQueryString();
  return routeUrl + request.getRequestURI().replace(prefix, "") +
    (queryString != null ? "?" + queryString : "");
 }
 
 private RequestEntity createRequestEntity(HttpServletRequest request, String url) throws URISyntaxException, IOException {
  String method = request.getMethod();
  HttpMethod httpMethod = HttpMethod.resolve(method);
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