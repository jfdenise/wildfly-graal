/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.quickstarts.helloworld.rest2;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import java.util.List;
/**
 * A simple REST service which is able to say "Hello World!"
 *
 * @author Ashwin Mehendale
 * @author emartins
 */

@Path("/")
public class HelloWorld2 {

    @Context
    HttpHeaders httpHeaders;
    @Context
    Request httpRequest;
    @GET
    @Path("/HelloWorld2")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello2(@QueryParam("from") int from,
@QueryParam("to") int to,
@QueryParam("orderBy") List<String> orderBy) {
        System.out.println("HEADERS" + httpHeaders.getRequestHeaders());
        System.out.println("REQUEST" + httpRequest.getMethod());
        System.out.println("FROM " + from + " TO " + to + "ORDER " + orderBy);
        return "Hello World!";
    }
}
