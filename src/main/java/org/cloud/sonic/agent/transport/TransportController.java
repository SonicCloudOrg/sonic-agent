package org.cloud.sonic.agent.transport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(TransportController.DELEGATE_PREFIX)
public class TransportController {

    public final static String DELEGATE_PREFIX = "/uia";

    @Autowired
    private RoutingDelegate routingDelegate;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity catchAll(HttpServletRequest request, HttpServletResponse response) {
        return routingDelegate.redirect(request, response, "http://localhost:6790", DELEGATE_PREFIX);
    }
}