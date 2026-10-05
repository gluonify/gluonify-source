package io.gluonify.source.security;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Set;

/**
 * DEVELOPMENT identity: under {@code mvn quarkus:dev} only, every request is "dev" with the read and write roles, to try the API and the UI
 * without an identity server.
 *
 * <p><b>Security: {@code @IfBuildProfile("dev")}</b>: this bean is compiled ONLY in the development profile. It does not exist in the production executable (neither native nor JVM):
 * it cannot be activated through an environment variable. In production, only the Charm token opens the API.
 */
@Alternative
@Priority(1000)
@ApplicationScoped
@IfBuildProfile("dev")
public class DevAuthentication implements HttpAuthenticationMechanism {
    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("dev")).addRoles(Set.of("source:read", "source:write")).build());
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of();
    }
}
