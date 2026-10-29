package com.scivicslab.keycloak.passport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The merge of the Visas that several Mappers add to the claim. No Keycloak. */
class PassportClaimTest {

    @Test
    void anAbsentClaimTakesTheAddedVisas() {
        assertEquals(List.of("a", "b"), PassportClaim.merge(null, List.of("a", "b")));
    }

    @Test
    void theAddedVisasFollowTheVisasAlreadyInTheClaim() {
        assertEquals(List.of("a", "b", "c"), PassportClaim.merge(List.of("a", "b"), List.of("c")));
    }

    @Test
    void theSameVisaIsKeptOnce() {
        assertEquals(List.of("a", "b"), PassportClaim.merge(List.of("a"), List.of("a", "b")));
    }

    @Test
    void aClaimThatIsNotAListHoldsNoVisas() {
        assertEquals(List.of("a"), PassportClaim.merge("not a list", List.of("a")));
    }

    @Test
    void addingNoVisasKeepsTheClaim() {
        assertEquals(List.of("a"), PassportClaim.merge(List.of("a"), List.of()));
    }
}
