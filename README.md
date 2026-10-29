# keycloak-ga4gh-passport-mapper

A Keycloak Protocol Mapper that makes a Keycloak realm a GA4GH Passport **Broker**: when the UserInfo Endpoint
answers, it asks a Visa issuer for the Visas of the user and adds them, as Visa JWTs, to the array in the claim
`ga4gh_passport_v1`. The setting `passport.url` takes the URLs of several Visa issuers, one per line; the Visas of all the
issuers go into one Passport, in the order of the lines. Several mappers on the same client scope collect their Visas into
one Passport too.

- It acts on the **UserInfo Endpoint only**, never on the ID token or the access token (the GA4GH AAI profile does not
  allow GA4GH claims in a Passport-Scoped Access Token).
- It does not check or sign the Visas. The signature belongs to the Visa issuer; the Passport Clearinghouse verifies it.
- The issuers are asked at the same time, so the wait is that of the slowest one. When an issuer cannot be reached, the
  UserInfo response still comes, with the Visas of the other issuers (or without the claim when no issuer answered).
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
   - `passport.url`: the URL of a Visa issuer, for example `http://ga4gh-visa-issuer:8080/passport/{username}`. For several
     issuers, one URL per line.
   - `passport.timeout.seconds`: default `5`
2. Add the client scope to the Relying Party's client as an **optional** client scope.
3. The Relying Party requests `scope=openid ga4gh_passport_v1` and calls the UserInfo Endpoint with the access token.

## Build and test

```bash
rm -rf target && mvn package    # 19 unit tests; the fetch of a Passport and the collection of the Visas of several issuers are tested without Keycloak
```

## License

Apache License, Version 2.0. See [LICENSE](LICENSE).
