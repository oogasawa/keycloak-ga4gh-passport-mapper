package com.scivicslab.keycloak.passport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fetches the Visas of a user from a Visa issuer: a GET that answers {"ga4gh_passport_v1": ["<Visa JWT>", ...]}.
 * It does not check the Visas; the signature belongs to the Visa issuer, and the Passport Clearinghouse checks it.
 */
public final class PassportFetcher {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // an h2c upgrade attempt breaks some servers
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** The URL of a user's Passport: the template with {username} replaced by the URL-encoded user name. */
    public static String urlFor(String template, String username) {
        // URLEncoder writes a space as "+", which is a space only in a query string; in a path it must be %20.
        return template.replace("{username}", URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /** The Visa JWTs of the Passport at the URL; throws when the issuer does not answer with a Passport claim. */
    public List<String> fetch(String url, int timeoutSeconds) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException(url + " answered HTTP " + response.statusCode());
        }
        Map<String, Object> claim = JSON.readValue(response.body(), new TypeReference<>() {
        });
        if (!(claim.get("ga4gh_passport_v1") instanceof List<?> visas)) {
            throw new IllegalStateException(url + " did not answer with a ga4gh_passport_v1 array");
        }
        return visas.stream().map(String::valueOf).toList();
    }
}
