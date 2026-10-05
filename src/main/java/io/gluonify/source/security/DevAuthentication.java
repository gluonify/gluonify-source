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
 * Identité de DÉVELOPPEMENT : sous {@code mvn quarkus:dev} uniquement, toute requête est « dev » avec les rôles de lecture et d'écriture, pour essayer l'API et l'interface
 * sans serveur d'identité.
 *
 * <p><b>Sécurité : {@code @IfBuildProfile("dev")}</b> : ce bean n'est compilé QUE dans le profil de développement. Il n'existe pas dans l'exécutable de production (ni natif ni JVM) :
 * il est impossible de l'activer par une variable d'environnement. En production, seul le jeton de Charm ouvre l'API.
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
