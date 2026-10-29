package com.scivicslab.keycloak.passport;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The Visas that the claim {@code ga4gh_passport_v1} carries when several Mappers each add the Visas of their own
 * Visa issuer. Keycloak runs the Mappers of a client scope one after another on the same token, so a Mapper that
 * replaced the claim would drop the Visas of the Mappers before it.
 */
public final class PassportClaim {

    private PassportClaim() {
    }

    /**
     * The Visas already in the claim, in their order, followed by the added Visas that are not in the claim yet.
     * The same Visa JWT is kept once. A claim that is absent or is not a list holds no Visas.
     */
    public static List<String> merge(Object existing, List<String> added) {
        LinkedHashSet<String> visas = new LinkedHashSet<>();
        if (existing instanceof List<?> list) {
            for (Object visa : list) {
                visas.add(String.valueOf(visa));
            }
        }
        visas.addAll(added);
        return new ArrayList<>(visas);
    }
}
