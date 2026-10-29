package com.scivicslab.keycloak.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.AccessToken;

/**
 * The Passport that the UserInfo response carries when several Mappers of this class are attached to the
 * client scope, each with the URL of its own Visa issuer. The Visa issuers are local HTTP servers; the
 * Keycloak models are stubs. No Keycloak runs.
 */
class Ga4ghPassportMapperTest {

    private HttpServer issuerOne;
    private HttpServer issuerTwo;
    private HttpServer failingIssuer;

    @BeforeEach
    void start() throws Exception {
        issuerOne = issuer("{\"ga4gh_passport_v1\":[\"one.a.a\",\"shared.s.s\"]}", 200);
        issuerTwo = issuer("{\"ga4gh_passport_v1\":[\"two.b.b\",\"shared.s.s\"]}", 200);
        failingIssuer = issuer("error", 500);
    }

    @AfterEach
    void stop() {
        issuerOne.stop(0);
        issuerTwo.stop(0);
        failingIssuer.stop(0);
    }

    private static HttpServer issuer(String body, int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/passport/alice", ex -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return server;
    }

    private static ProtocolMapperModel mapperFor(HttpServer server) {
        ProtocolMapperModel model = new ProtocolMapperModel();
        model.setConfig(Map.of(
                Ga4ghPassportMapper.CONFIG_URL, "http://127.0.0.1:" + server.getAddress().getPort() + "/passport/{username}",
                Ga4ghPassportMapper.CONFIG_TIMEOUT, "3",
                "userinfo.token.claim", "true"));
        return model;
    }

    private static UserSessionModel sessionOf(String username) {
        UserModel user = (UserModel) Proxy.newProxyInstance(UserModel.class.getClassLoader(), new Class<?>[] {UserModel.class},
                (proxy, method, args) -> "getUsername".equals(method.getName()) ? username : null);
        return (UserSessionModel) Proxy.newProxyInstance(UserSessionModel.class.getClassLoader(), new Class<?>[] {UserSessionModel.class},
                (proxy, method, args) -> "getUser".equals(method.getName()) ? user : null);
    }

    private static List<?> claimOf(AccessToken token) {
        return (List<?>) token.getOtherClaims().get(Ga4ghPassportMapper.CLAIM);
    }

    private void run(Ga4ghPassportMapper mapper, AccessToken token, HttpServer issuer) {
        mapper.transformUserInfoToken(token, mapperFor(issuer), null, sessionOf("alice"), null);
    }

    private static ProtocolMapperModel mapperForUrls(String... urls) {
        ProtocolMapperModel model = new ProtocolMapperModel();
        model.setConfig(Map.of(
                Ga4ghPassportMapper.CONFIG_URL, String.join("\n", urls),
                Ga4ghPassportMapper.CONFIG_TIMEOUT, "3",
                "userinfo.token.claim", "true"));
        return model;
    }

    private static String urlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/passport/{username}";
    }

    private void runUrls(Ga4ghPassportMapper mapper, AccessToken token, String... urls) {
        mapper.transformUserInfoToken(token, mapperForUrls(urls), null, sessionOf("alice"), null);
    }

    @Test
    void twoMappersCollectTheVisasOfTheirIssuersIntoOnePassport() {
        AccessToken token = new AccessToken();
        Ga4ghPassportMapper mapper = new Ga4ghPassportMapper();
        run(mapper, token, issuerOne);
        run(mapper, token, issuerTwo);

        assertEquals(List.of("one.a.a", "shared.s.s", "two.b.b"), claimOf(token));
    }

    @Test
    void aMapperWhoseIssuerFailsLeavesTheVisasOfTheOtherMapper() {
        AccessToken token = new AccessToken();
        Ga4ghPassportMapper mapper = new Ga4ghPassportMapper();
        run(mapper, token, issuerOne);
        run(mapper, token, failingIssuer);

        assertEquals(List.of("one.a.a", "shared.s.s"), claimOf(token));
    }

    @Test
    void aFailingIssuerAloneSetsNoClaim() {
        AccessToken token = new AccessToken();
        run(new Ga4ghPassportMapper(), token, failingIssuer);

        assertFalse(token.getOtherClaims().containsKey(Ga4ghPassportMapper.CLAIM));
    }

    @Test
    void oneMapperWithTheUrlsOfTwoIssuersOnSeparateLinesCollectsBoth() {
        AccessToken token = new AccessToken();
        runUrls(new Ga4ghPassportMapper(), token, urlOf(issuerOne), urlOf(issuerTwo));

        assertEquals(List.of("one.a.a", "shared.s.s", "two.b.b"), claimOf(token));
    }

    @Test
    void theVisasFollowTheOrderOfTheUrls() {
        AccessToken token = new AccessToken();
        runUrls(new Ga4ghPassportMapper(), token, urlOf(issuerTwo), urlOf(issuerOne));

        assertEquals(List.of("two.b.b", "shared.s.s", "one.a.a"), claimOf(token));
    }

    @Test
    void anIssuerThatFailsLeavesTheVisasOfTheOtherUrlsOfTheSameMapper() {
        AccessToken token = new AccessToken();
        runUrls(new Ga4ghPassportMapper(), token, urlOf(failingIssuer), urlOf(issuerOne));

        assertEquals(List.of("one.a.a", "shared.s.s"), claimOf(token));
    }

    @Test
    void blankLinesBetweenTheUrlsAreIgnored() {
        AccessToken token = new AccessToken();
        runUrls(new Ga4ghPassportMapper(), token, "", urlOf(issuerOne), "   ", urlOf(issuerTwo), "");

        assertEquals(List.of("one.a.a", "shared.s.s", "two.b.b"), claimOf(token));
    }

    @Test
    void whenEveryIssuerOfAMapperFailsTheVisasAlreadyInTheClaimStay() {
        AccessToken token = new AccessToken();
        Ga4ghPassportMapper mapper = new Ga4ghPassportMapper();
        run(mapper, token, issuerOne);
        runUrls(mapper, token, urlOf(failingIssuer), urlOf(failingIssuer));

        assertEquals(List.of("one.a.a", "shared.s.s"), claimOf(token));
    }

    @Test
    void aMapperWithNoUrlSetsNoClaim() {
        AccessToken token = new AccessToken();
        runUrls(new Ga4ghPassportMapper(), token, "  ");

        assertFalse(token.getOtherClaims().containsKey(Ga4ghPassportMapper.CLAIM));
    }
}
