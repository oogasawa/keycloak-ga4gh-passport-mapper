package com.scivicslab.keycloak.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The fetch of a Passport from a Visa issuer, against a local HTTP server. No Keycloak. */
class PassportFetcherTest {

    private HttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/passport/alice", ex -> reply(ex, 200, "{\"ga4gh_passport_v1\":[\"a.b.c\",\"d.e.f\"]}"));
        server.createContext("/passport/nobody", ex -> reply(ex, 200, "{\"ga4gh_passport_v1\":[]}"));
        server.createContext("/passport/broken", ex -> reply(ex, 200, "{\"other\":1}"));
        server.createContext("/passport/failing", ex -> reply(ex, 500, "error"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private String url(String user) {
        return PassportFetcher.urlFor("http://127.0.0.1:" + server.getAddress().getPort() + "/passport/{username}", user);
    }

    @Test
    void theVisasOfTheUserAreReturnedInOrder() throws Exception {
        assertEquals(List.of("a.b.c", "d.e.f"), new PassportFetcher().fetch(url("alice"), 3));
    }

    @Test
    void aUserWithoutVisasGetsAnEmptyList() throws Exception {
        assertEquals(List.of(), new PassportFetcher().fetch(url("nobody"), 3));
    }

    @Test
    void anAnswerWithoutAPassportClaimIsAnError() {
        assertThrows(IllegalStateException.class, () -> new PassportFetcher().fetch(url("broken"), 3));
    }

    @Test
    void aFailingIssuerIsAnError() {
        assertThrows(IllegalStateException.class, () -> new PassportFetcher().fetch(url("failing"), 3));
    }

    @Test
    void theUserNameIsUrlEncodedIntoTheTemplate() {
        assertEquals("http://x/passport/a%20b%2Fc", PassportFetcher.urlFor("http://x/passport/{username}", "a b/c"));
    }
}
