package io.gluonify.source.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Which Authorization headers give a token to forward. */
class ForwardedTokenTest {
    @Test
    void onlyABearerTokenIsForwarded() {
        assertEquals("abc.def.ghi", ForwardedToken.bearer("Bearer abc.def.ghi"));
        assertEquals("abc.def.ghi", ForwardedToken.bearer("bearer   abc.def.ghi  "), "the scheme is case-insensitive; spaces around the token are not part of it");
        assertNull(ForwardedToken.bearer(null));
        assertNull(ForwardedToken.bearer(""));
        assertNull(ForwardedToken.bearer("Bearer"));
        assertNull(ForwardedToken.bearer("Bearer    "));
        assertNull(ForwardedToken.bearer("Basic dXNlcjpwYXNz"), "credentials of another scheme are never forwarded");
        assertNull(ForwardedToken.bearer("Bearerabc"));
    }
}
