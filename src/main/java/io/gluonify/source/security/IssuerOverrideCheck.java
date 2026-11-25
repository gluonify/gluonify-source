package io.gluonify.source.security;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

/**
 * Startup check of {@code GLUONIFY_SOURCE_OIDC_ISSUER} against the realm whose keys verify the tokens.
 *
 * <p>{@code quarkus.oidc.auth-server-url} reads discovery and keys from the realm named by {@code GLUONIFY_SOURCE_OIDC_REALM}, else {@code GLUONIFY_OIDC_REALM}
 * (injected by the platform for an application of an organization), while {@code GLUONIFY_SOURCE_OIDC_ISSUER} replaces the issuer the tokens must carry. An
 * override left over from a deployment without an organization therefore makes every token of the organization's realm refused. When the override is not
 * {@code <Charm's public or internal base>/realms/<realm>} (scheme and host compared in lower case, without default port nor trailing slash; with no known base,
 * only the realm part is compared), ONE warning is logged at startup. The override still wins (refusing to start is an open question of {@code plan.md}); the
 * message names the variables and the realm parts only, never a token, a key or a credential, and nothing of it reaches a client. The override {@code any}
 * disables the issuer check of Quarkus OIDC, so it refuses no token and is not reported.
 *
 * @author David CABILLIC
 */
@ApplicationScoped
public class IssuerOverrideCheck {

    /** The application's own issuer override. */
    static final String SOURCE_ISSUER = "GLUONIFY_SOURCE_OIDC_ISSUER";

    /** The application's own realm override, read first by {@code quarkus.oidc.auth-server-url}. */
    static final String SOURCE_REALM = "GLUONIFY_SOURCE_OIDC_REALM";

    /** The organization's realm, injected by the platform. */
    static final String OIDC_REALM = "GLUONIFY_OIDC_REALM";

    /** Where Charm's address can be read (public or internal); an issuer's {@code /realms/...} part is cut off. */
    private static final List<String> CHARM_BASES = List.of("GLUONIFY_SOURCE_CHARM_PUBLIC_URL", "GLUONIFY_OIDC_PUBLIC_URL", "GLUONIFY_OIDC_ISSUER",
            "GLUONIFY_SERVICE_CHARM_URL");

    /** The warning: the override's variable, what is wrong with it, the realm's variable and the realm. */
    private static final String MESSAGE = "%1$s %2$s, but %3$s is '%4$s': tokens are verified with the keys of realm '%4$s' and must carry the override's issuer, "
            + "so the realm's tokens are refused. Remove the override or set it to <Charm>/realms/%4$s.";

    private static final String REALMS = "/realms/";
    private static final String ANY_ISSUER = "any";
    private static final Map<String, Integer> DEFAULT_PORTS = Map.of("http", 80, "https", 443);
    private static final Logger LOG = Logger.getLogger(IssuerOverrideCheck.class);

    private final Config config;

    /**
     * Creates the check over the application's configuration (environment variables included).
     *
     * @param config The configuration the variables are read from
     */
    @Inject
    public IssuerOverrideCheck(final Config config) {

        this.config = config;
    }

    /** Logs the warning once at startup when the issuer override contradicts the realm. */
    @Startup
    void logIfContradicted() {

        warning(name -> config.getOptionalValue(name, String.class)).ifPresent(LOG::warn);
    }

    /**
     * The warning to log when {@code GLUONIFY_SOURCE_OIDC_ISSUER} is not the issuer of the realm whose keys verify the tokens.
     *
     * @param env Reads a variable (absent or blank: not set)
     * @return The message, empty when there is no override, no realm, the override is {@code any} or it is the realm's issuer
     */
    static Optional<String> warning(final Function<String, Optional<String>> env) {

        final Optional<String> override = value(env, SOURCE_ISSUER).filter(v -> !ANY_ISSUER.equals(v));
        final Optional<String> realmVariable = Stream.of(SOURCE_REALM, OIDC_REALM).filter(k -> value(env, k).isPresent()).findFirst();
        if (override.isEmpty() || realmVariable.isEmpty()) return Optional.empty();
        final String realm = value(env, realmVariable.get()).orElseThrow();
        final Optional<Url> issuer = normalised(override.get());
        if (issuer.isPresent() && isIssuerOf(issuer.get(), realm, charmBases(env))) return Optional.empty();
        final String reason = issuer.map(u -> mismatch(u, realm)).orElse("is not an http(s) URL");
        return Optional.of(MESSAGE.formatted(SOURCE_ISSUER, reason, realmVariable.get(), realm));
    }

    private static Optional<String> value(final Function<String, Optional<String>> env, final String name) {

        return env.apply(name).map(String::strip).filter(v -> !v.isEmpty());
    }

    /** {@code (scheme, host[:port], path)} in lower case without default port nor trailing slash; empty when not an http(s) URL with a host. */
    private static Optional<Url> normalised(final String url) {

        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
        final String scheme = Objects.requireNonNullElse(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        final Integer defaultPort = DEFAULT_PORTS.get(scheme);
        if (defaultPort == null || uri.getHost() == null) return Optional.empty();
        final String host = uri.getHost().toLowerCase(Locale.ROOT);
        final int port = uri.getPort();
        final String authority = port < 0 || port == defaultPort ? host : host + ":" + port;
        return Optional.of(new Url(scheme, authority, stripTrailingSlashes(uri.getPath())));
    }

    private static Set<Url> charmBases(final Function<String, Optional<String>> env) {

        return CHARM_BASES.stream().map(k -> value(env, k).flatMap(IssuerOverrideCheck::normalised)).flatMap(Optional::stream).map(Url::base)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isIssuerOf(final Url issuer, final String realm, final Set<Url> bases) {

        if (bases.isEmpty()) return realmPart(issuer.path()).equals(realm);
        return bases.stream().anyMatch(b -> issuer.equals(new Url(b.scheme(), b.host(), b.path() + REALMS + realm)));
    }

    /** What is wrong with the override, by its realm part only. */
    private static String mismatch(final Url issuer, final String realm) {

        final String found = realmPart(issuer.path());
        if (found.isEmpty()) return "has no /realms/<realm> part";
        return found.equals(realm) ? "is not under Charm's address" : "names realm '%s'".formatted(found);
    }

    private static String realmPart(final String path) {

        final int i = path.lastIndexOf(REALMS);
        return i < 0 ? "" : path.substring(i + REALMS.length());
    }

    /** Linear-time replacement of {@code replaceAll("/+$", "")}, which backtracks on a long run of slashes. */
    private static String stripTrailingSlashes(final String v) {

        int end = v.length();
        while (end > 0 && v.charAt(end - 1) == '/') end--;
        return v.substring(0, end);
    }

    /**
     * A normalised http(s) URL.
     *
     * @param scheme {@code http} or {@code https}
     * @param host   The host in lower case, with its port unless it is the scheme's default
     * @param path   The path without trailing slash
     * @author David CABILLIC
     */
    private record Url(String scheme, String host, String path) {

        /** This URL without its {@code /realms/...} part: Charm's base. */
        Url base() {

            final int i = path.indexOf(REALMS);
            return i < 0 ? this : new Url(scheme, host, path.substring(0, i));
        }
    }
}
