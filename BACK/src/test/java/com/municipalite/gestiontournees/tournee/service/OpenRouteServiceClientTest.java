package com.municipalite.gestiontournees.tournee.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OpenRouteServiceClientTest {
    HttpServer server; OpenRouteServiceClient client;
    String body; int status;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            assertEquals("test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        RoutingProperties properties=new RoutingProperties(); properties.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()); properties.setApiKey("test-key");
        client=new OpenRouteServiceClient(properties,new ObjectMapper()); status=200;
    }
    @AfterEach void stop() { server.stop(0); }
    @Test void preservesDirectedMatrixAndRejectsUnreachableArc() {
        body="{\"durations\":[[0,3],[7,0]],\"distances\":[[0,8],[9,0]]}";
        var matrix=client.matrix(List.of(List.of(10.,36.),List.of(10.1,36.1)));
        assertEquals(3,matrix.durations()[0][1]); assertEquals(7,matrix.durations()[1][0]);
        body="{\"durations\":[[0,null],[7,0]],\"distances\":[[0,8],[9,0]]}";
        assertEquals(422,assertThrows(ResponseStatusException.class,()->client.matrix(List.of(List.of(10.,36.),List.of(10.1,36.1)))).getStatusCode().value());
    }
    @Test void invalidGeometryCannotReachMaps() {
        body="{\"features\":[{\"geometry\":{\"coordinates\":[[300,36],[10,36]]}}]}";
        assertEquals(503,assertThrows(ResponseStatusException.class,()->client.geometry(List.of(List.of(10.,36.),List.of(10.1,36.1)))).getStatusCode().value());
    }
    @Test void providerOutageHasNoStraightLineFallback() {
        body="{}"; status=429;
        assertEquals(503,assertThrows(ResponseStatusException.class,()->client.matrix(List.of(List.of(10.,36.),List.of(10.1,36.1)))).getStatusCode().value());
    }
}
