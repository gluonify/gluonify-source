package io.gluonify.source.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.smallrye.config.SmallRyeConfigBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code GLUONIFY_SOURCE_OIDC_ISSUER} against the realm whose keys verify the tokens: a mismatch gives one warning, a match or a missing side gives none, and a
 * malformed value never throws ({@link IssuerOverrideCheck} without Quarkus; a started application is {@code IssuerOverrideStartupTest}).
 *
 * @author David CABILLIC
 */
class IssuerOverrideCheckTest {

    private static final String ORG = "https://id.gluonify.lan/realms/org-acme";
    private static final String PLATFORM_ISSUER = "https://id.gluonify.lan/realms/gluonify";
    private static final String ORG_PREFIX = "GLUONIFY_SOURCE_OIDC_ISSUER %s, but GLUONIFY_OIDC_REALM is 'org-acme'";

    /** The variables the platform injects for an application of the organization acme. */
    private static final Map<String, String> ORG_PLATFORM = Map.of("GLUONIFY_OIDC_REALM", "org-acme", "GLUONIFY_OIDC_ISSUER", ORG,
            "GLUONIFY_OIDC_PUBLIC_URL", "https://id.gluonify.lan/", "GLUONIFY_SERVICE_CHARM_URL", "http://charm:8080", "GLUONIFY_OIDC_CLIENT_ID", "app-source");

    private static Optional<String> warning(final Map<String, String> env) {

        return IssuerOverrideCheck.warning(name -> Optional.ofNullable(env.get(name)));
    }

    private static Map<String, String> with(final Map<String, String> env, final String... pairs) {

        final Map<String, String> out = new HashMap<>(env);
        for (int i = 0; i < pairs.length; i += 2) out.put(pairs[i], pairs[i + 1]);
        return Map.copyOf(out);
    }

    /** Collects the records of the check's logger. */
    private static final class Records extends Handler {

        private final List<LogRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void publish(final LogRecord logRecord) {

            records.add(logRecord);
        }

        @Override
        public void flush() {
            // nothing buffered
        }

        @Override
        public void close() {
            // nothing to release
        }
    }

    /** The warnings the startup check logs over this configuration. */
    private static List<String> loggedWarnings(final Map<String, String> env) {

        final IssuerOverrideCheck check = new IssuerOverrideCheck(new SmallRyeConfigBuilder().withDefaultValues(env).build());
        final Logger logger = Logger.getLogger(IssuerOverrideCheck.class.getName());
        final Records handler = new Records();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            check.logIfContradicted();
        } finally {
            logger.removeHandler(handler);
        }
        return handler.records.stream().filter(r -> r.getLevel().intValue() == Level.WARNING.intValue()).map(LogRecord::getMessage).toList();
    }

    @Test
    void theStartupCheckLogsOneWarningOnAMismatchAndNoneOnAMatch() {

        final List<String> warnings = loggedWarnings(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, PLATFORM_ISSUER));
        assertEquals(1, warnings.size(), "one warning per start");
        assertTrue(warnings.getFirst().startsWith(ORG_PREFIX.formatted("names realm 'gluonify'")), warnings.getFirst());
        assertEquals(List.of(), loggedWarnings(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, ORG)));
    }

    @Test
    void platformIssuerOverrideInAnOrganizationWarns() {

        final String message = warning(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, PLATFORM_ISSUER)).orElseThrow();
        assertTrue(message.startsWith(ORG_PREFIX.formatted("names realm 'gluonify'")), message);
        assertTrue(message.contains("<Charm>/realms/org-acme"), message);
        assertFalse(message.contains("id.gluonify.lan"), "the message names the realm parts only, never an address");
        assertFalse(message.contains("charm:8080"), "the message names the realm parts only, never an address");
    }

    @ParameterizedTest
    @ValueSource(strings = {ORG, "HTTPS://ID.Gluonify.LAN/realms/org-acme/", "https://id.gluonify.lan:443/realms/org-acme", "http://charm:8080/realms/org-acme"})
    void overrideMatchingTheRealmIsSilent(final String override) {

        assertEquals(Optional.empty(), warning(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, override)));
    }

    @Test
    void overrideWithoutRealmIsSilent() {

        final Map<String, String> env = Map.of(IssuerOverrideCheck.SOURCE_ISSUER, "https://id.example.com/realms/other", "GLUONIFY_SERVICE_CHARM_URL",
                "http://charm:8080");
        assertEquals(Optional.empty(), warning(env));
        assertEquals(Optional.empty(), warning(with(env, IssuerOverrideCheck.OIDC_REALM, "  ")));
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "any"})
    void realmWithoutOverrideIsSilent(final String override) {

        assertEquals(Optional.empty(), warning(ORG_PLATFORM));
        assertEquals(Optional.empty(), warning(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, override)),
                "a blank override is no override, and 'any' turns the issuer check of Quarkus OIDC off: no token is refused");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "not a url|is not an http(s) URL",
        "ftp://id.gluonify.lan/realms/org-acme|is not an http(s) URL",
        "/realms/org-acme|is not an http(s) URL",
        "https://[::1/realms/org-acme|is not an http(s) URL",
        "https://id.gluonify.lan:port/realms/org-acme|is not an http(s) URL",
        "https:///realms/org-acme|is not an http(s) URL",
        "https://id.gluonify.lan|has no /realms/<realm> part",
        "https://elsewhere.example/realms/org-acme|is not under Charm's address",
        "https://id.gluonify.lan:8443/realms/org-acme|is not under Charm's address"
    })
    void malformedOrForeignOverrideWarnsWithoutThrowing(final String override, final String reason) {

        final String message = warning(with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_ISSUER, override)).orElseThrow();
        assertTrue(message.startsWith(ORG_PREFIX.formatted(reason)), message);
    }

    @Test
    void withoutAnyCharmAddressOnlyTheRealmPartIsCompared() {

        final Map<String, String> env = Map.of(IssuerOverrideCheck.OIDC_REALM, "org-acme");
        assertEquals(Optional.empty(), warning(with(env, IssuerOverrideCheck.SOURCE_ISSUER, "https://anywhere.example/realms/org-acme")));
        final String message = warning(with(env, IssuerOverrideCheck.SOURCE_ISSUER, "https://anywhere.example/realms/org-other")).orElseThrow();
        assertTrue(message.contains("names realm 'org-other'"), message);
    }

    @Test
    void theApplicationsOwnRealmIsTheOneTheKeysFollow() {

        final Map<String, String> env = with(ORG_PLATFORM, IssuerOverrideCheck.SOURCE_REALM, "team", IssuerOverrideCheck.SOURCE_ISSUER, ORG);
        final String message = warning(env).orElseThrow();
        assertTrue(message.startsWith("GLUONIFY_SOURCE_OIDC_ISSUER names realm 'org-acme', but GLUONIFY_SOURCE_OIDC_REALM is 'team'"), message);
        assertEquals(Optional.empty(), warning(with(env, IssuerOverrideCheck.SOURCE_ISSUER, "https://id.gluonify.lan/realms/team")));
        assertEquals(Optional.empty(), warning(Map.of(IssuerOverrideCheck.SOURCE_REALM, "team", IssuerOverrideCheck.SOURCE_ISSUER, "https://charm.example/realms/team",
                "GLUONIFY_SERVICE_CHARM_URL", "https://charm.example")), "the application's own realm alone is checked too");
    }
}
