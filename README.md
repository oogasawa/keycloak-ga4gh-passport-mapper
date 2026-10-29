# keycloak-ga4gh-passport-mapper

A Keycloak Protocol Mapper that makes a Keycloak realm a GA4GH Passport **Broker**: when the UserInfo Endpoint
answers, it asks a Visa issuer for the Visas of the user and adds them, as Visa JWTs, to the array in the claim
`ga4gh_passport_v1`. Several mappers, each with the URL of its own Visa issuer, collect the Visas of all the issuers
into one Passport.

- It acts on the **UserInfo Endpoint only**, never on the ID token or the access token (the GA4GH AAI profile does not
  allow GA4GH claims in a Passport-Scoped Access Token).
- It does not check or sign the Visas. The signature belongs to the Visa issuer; the Passport Clearinghouse verifies it.
- When a Visa issuer cannot be reached, the UserInfo response still comes, with the Visas of the other issuers (or without
  the claim when no issuer answered).
- A mapper adds its Visas after the Visas that the mappers before it put in the claim. The same Visa JWT is kept once.

The Protocol Mapper SPI is an internal SPI of Keycloak (Keycloak logs `KC-SERVICES0047` when it loads this jar), so
the jar has to be built against, and tested with, the Keycloak version it runs on. It is built against Keycloak 26.8.1.

## Visa issuer contract

`GET <passport.url>` with `{username}` replaced by the (URL-encoded) user name must answer `200` and
`{"ga4gh_passport_v1": ["<Visa JWT>", ...]}`. [ga4gh-visa-issuer](https://github.com/oogasawa/ga4gh-visa-issuer)
(`GET /passport/{sub}`) follows it.

## Install

```bash
rm -rf target && mvn package
cp target/keycloak-ga4gh-passport-mapper-*.jar /opt/keycloak/providers/
/opt/keycloak/bin/kc.sh build        # not needed for start-dev
```

## Configure (admin console or REST API)

1. Create a client scope named `ga4gh_passport_v1` and add this mapper to it
   (mapper type `ga4gh-passport-mapper`, **Add to userinfo** on):
   - `passport.url`: for example `http://ga4gh-visa-issuer:8080/passport/{username}`
   - `passport.timeout.seconds`: default `5`
2. Add the client scope to the Relying Party's client as an **optional** client scope.
   To collect the Visas of several Visa issuers, add one mapper per issuer to the same client scope, each with the
   `passport.url` of its issuer.
3. The Relying Party requests `scope=openid ga4gh_passport_v1` and calls the UserInfo Endpoint with the access token.

## Build and test

```bash
rm -rf target && mvn package    # 13 unit tests; the fetch of a Passport and the collection of the Visas of two issuers are tested without Keycloak
```

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
