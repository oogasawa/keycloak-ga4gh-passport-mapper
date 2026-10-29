package com.scivicslab.keycloak.passport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.AccessToken;

/**
 * A Protocol Mapper that makes Keycloak a GA4GH Passport Broker: when the UserInfo Endpoint answers, it asks a
 * Visa issuer for the Visas of the user and adds them, as Visa JWTs, to the array in the claim
 * {@code ga4gh_passport_v1}. Several Mappers of this class, each with the URL of its own Visa issuer, collect the
 * Visas of all the issuers into one Passport. It acts on the UserInfo Endpoint only, never on the ID token or the access token
 * (a Passport-Scoped Access Token must not carry GA4GH claims). Attach it to a client scope named
 * {@code ga4gh_passport_v1} that the Relying Party requests as an optional scope.
 */
public class Ga4ghPassportMapper extends AbstractOIDCProtocolMapper implements UserInfoTokenMapper {

    public static final String PROVIDER_ID = "ga4gh-passport-mapper";
    public static final String CLAIM = "ga4gh_passport_v1";
    public static final String CONFIG_URL = "passport.url";
    public static final String CONFIG_TIMEOUT = "passport.timeout.seconds";

    private static final Logger LOG = Logger.getLogger(Ga4ghPassportMapper.class);
    private static final PassportFetcher FETCHER = new PassportFetcher();
    private static final ExecutorService ISSUER_CALLS = Executors.newVirtualThreadPerTaskExecutor();
    private static final List<ProviderConfigProperty> CONFIG = new ArrayList<>();

    static {
        ProviderConfigProperty url = new ProviderConfigProperty();
        url.setName(CONFIG_URL);
        url.setLabel("Passport URLs (one per line)");
        url.setType(ProviderConfigProperty.TEXT_TYPE);
        url.setHelpText("URLs of the Visa issuers, one per line. Each URL answers {\"ga4gh_passport_v1\": [...]} for a user; "
                + "{username} is replaced by the user name. The Visas of all the issuers go into one Passport, in the order of the lines.");
        CONFIG.add(url);
        ProviderConfigProperty timeout = new ProviderConfigProperty();
        timeout.setName(CONFIG_TIMEOUT);
        timeout.setLabel("Timeout (seconds)");
        timeout.setType(ProviderConfigProperty.STRING_TYPE);
        timeout.setDefaultValue("5");
        timeout.setHelpText("How long to wait for the Visa issuer.");
        CONFIG.add(timeout);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG, Ga4ghPassportMapper.class);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "GA4GH Passport (Visas from a Visa issuer)";
    }

    @Override
    public String getHelpText() {
        return "Puts the Visas that a Visa issuer holds for the user in the claim ga4gh_passport_v1 of the UserInfo response.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }

    @Override
    public AccessToken transformUserInfoToken(AccessToken token, ProtocolMapperModel mappingModel, KeycloakSession session,
            UserSessionModel userSession, ClientSessionContext clientSessionCtx) {
        if (!OIDCAttributeMapperHelper.includeInUserInfo(mappingModel)) {
            return token;
        }
        List<String> templates = PassportFetcher.urlTemplates(mappingModel.getConfig().get(CONFIG_URL));
        if (templates.isEmpty()) {
            LOG.warn("The GA4GH Passport mapper has no Passport URL; the claim is not set");
            return token;
        }
        String username = userSession.getUser().getUsername();
        int timeout = Integer.parseInt(mappingModel.getConfig().getOrDefault(CONFIG_TIMEOUT, "5"));
        // The issuers are asked at the same time, so the wait is that of the slowest one, not the sum.
        List<CompletableFuture<List<String>>> answers = templates.stream()
                .map(template -> CompletableFuture.supplyAsync(() -> fetchOrNothing(template, username, timeout), ISSUER_CALLS))
                .toList();
        List<String> visas = new ArrayList<>();
        boolean anyIssuerAnswered = false;
        for (CompletableFuture<List<String>> answer : answers) {
            List<String> fromOneIssuer = answer.join();
            if (fromOneIssuer != null) {
                anyIssuerAnswered = true;
                visas = PassportClaim.merge(visas, fromOneIssuer);
            }
        }
        // No Visas is a safe answer: the Passport Clearinghouse then grants nothing. When no issuer answered, the claim
        // is left as the Mappers before this one made it.
        if (anyIssuerAnswered) {
            // The Visas of the Mappers that ran before this one stay: a Passport collects the Visas of every issuer.
            token.getOtherClaims().put(CLAIM, PassportClaim.merge(token.getOtherClaims().get(CLAIM), visas));
        }
        return token;
    }

    /** The Visas one issuer answers, or null when the issuer does not answer a Passport. */
    private static List<String> fetchOrNothing(String template, String username, int timeoutSeconds) {
        try {
            return FETCHER.fetch(PassportFetcher.urlFor(template, username), timeoutSeconds);
        } catch (Exception e) {
            LOG.warnf("Visas of %s could not be fetched from %s: %s", username, template, e.toString());
            return null;
        }
    }
}
