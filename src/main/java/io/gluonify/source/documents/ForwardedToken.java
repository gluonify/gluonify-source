package io.gluonify.source.documents;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import java.util.function.Supplier;

/**
 * The bearer token of the request being served, handed to the Crystallon client ({@code CrystallonTokens.forwarding}): Crystallon sees the person who called this
 * application, with the audience and the roles of THEIR token, not an identity of the application.
 *
 * <p>Honestly: the platform does not issue "application to service" tokens yet. Until it does, the caller's token must itself carry the audience {@code gluonify-crystallon}
 * and the role {@code gluonify:crystallon:render} (Charm can put several audiences in one token), and a call made outside a request (a timer, a webhook...) has no token and is
 * refused by Crystallon with a 401. The token is read from the {@code Authorization} header, so it works the same under {@code mvn quarkus:dev}, in tests and in production.
 * It is never logged and never copied anywhere else.
 */
@ApplicationScoped
public class ForwardedToken implements Supplier<String> {
    @Inject
    CurrentVertxRequest request;

    /** @return the token of the current request, or {@code null} when there is none (no request, no {@code Authorization} header, not a bearer token) */
    @Override
    public String get() {
        try {
            RoutingContext current = request.getCurrent();
            return current == null ? null : bearer(current.request().getHeader("Authorization"));
        } catch (ContextNotActiveException e) {
            return null; // outside a request
        }
    }

    /** The token of an {@code Authorization: Bearer <token>} header value (the scheme is case-insensitive); {@code null} for anything else. */
    static String bearer(String authorization) {
        if (authorization == null || authorization.length() < 8 || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = authorization.substring(7).strip();
        return token.isEmpty() ? null : token;
    }
}
