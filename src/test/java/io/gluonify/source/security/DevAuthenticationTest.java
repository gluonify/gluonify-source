package io.gluonify.source.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The development identity (the bean only exists in the dev profile, so it is exercised directly): "dev", with both roles, and no challenge nor credential type. */
class DevAuthenticationTest {
    private final DevAuthentication dev = new DevAuthentication();

    @Test
    void everyRequestIsTheDevUserWithTheReadAndWriteRoles() {
        final var identity = dev.authenticate(null, null).await().indefinitely();
        assertEquals("dev", identity.getPrincipal().getName());
        assertEquals(Set.of("source:read", "source:write"), identity.getRoles());
    }

    @Test
    void thereIsNoChallengeAndNoCredentialType() {
        assertNull(dev.getChallenge(null).await().indefinitely());
        assertTrue(dev.getCredentialTypes().isEmpty());
    }
}
