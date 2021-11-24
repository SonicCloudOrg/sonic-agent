package com.sonic.agent.websockets;

import com.sonic.agent.automation.RemoteDebugDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(GraphDBController.DELEGATE_PREFIX)
public class GraphDBController {
 
 public final static String DELEGATE_PREFIX = "/agent";
 
 @Autowired
 private RoutingDelegate routingDelegate;
 
 @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}, produces = MediaType.TEXT_PLAIN_VALUE)
 public ResponseEntity catchAll(HttpServletRequest request, HttpServletResponse response) {
  System.out.println("http://localhost:" + RemoteDebugDriver.port + "/devtools");
  return routingDelegate.redirect(request, response, "http://localhost:" + RemoteDebugDriver.port + "/devtools", DELEGATE_PREFIX);
 }
}