package io.gluonify.source.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.gluonify.plasma.quarkus.tls.InternalRootConfigSourceFactory;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Charm served over the platform's internal TLS (round 28: 500 on {@code GET /api/notes}, "PKIX path building failed" in the OIDC discovery): the SDK's
 * {@link InternalRootConfigSourceFactory} is on the classpath and registered as a service, and with this application's own {@code application.properties} it points
 * the default OIDC tenant at the TLS configuration that trusts the cluster's root ({@code GLUONIFY_CA_FILE}); without the root, or with Charm over plain HTTP, the
 * tenant keeps the JDK's authorities.
 *
 * @author David CABILLIC
 */
class InternalRootTrustTest {

    private static final String CHARM = "https://192.168.1.4:31002";

    private static final String TENANT_TLS = "quarkus.oidc.tls.tls-configuration-name";

    private static final String ROOT_FILE = "quarkus.tls." + InternalRootConfigSourceFactory.CONFIGURATION + ".trust-store.pem.certs";

    @TempDir
    Path dir;

    @Test
    void theSdkRegistersTheFactoryAsAConfigSourceFactory() {

        final List<ConfigSourceFactory> factories = ServiceLoader.load(ConfigSourceFactory.class).stream().map(ServiceLoader.Provider::get)
                .filter(InternalRootConfigSourceFactory.class::isInstance).toList();
        assertEquals(1, factories.size(), "gluonify-plasma-quarkus must bring gluonify-plasma-quarkus-tls (SDK 0.7.4 does not: every Charm call fails over internal TLS)");
    }

    @Test
    void theDefaultTenantTrustsTheClustersRootWhenCharmIsServedOverTls() throws IOException {

        final String root = root();
        final SmallRyeConfig config = config(Map.of("GLUONIFY_CA_FILE", root, "GLUONIFY_SERVICE_CHARM_URL", CHARM, "GLUONIFY_OIDC_REALM", "org-acme"));
        assertEquals(CHARM + "/realms/org-acme", config.getConfigValue("quarkus.oidc.auth-server-url").getValue());
        assertEquals(InternalRootConfigSourceFactory.CONFIGURATION, config.getConfigValue(TENANT_TLS).getValue());
        assertEquals(root, config.getConfigValue(ROOT_FILE).getValue());
    }

    @Test
    void charmOverPlainHttpLeavesTheTenantAlone() throws IOException {

        final SmallRyeConfig config = config(Map.of("GLUONIFY_CA_FILE", root(), "GLUONIFY_SERVICE_CHARM_URL", "http://192.168.1.4:31002"));
        assertTrue(config.getOptionalValue(TENANT_TLS, String.class).isEmpty());
    }

    @Test
    void withoutTheRootTheTenantKeepsTheJdksAuthorities() throws IOException {

        final SmallRyeConfig config = config(Map.of("GLUONIFY_SERVICE_CHARM_URL", CHARM));
        assertTrue(config.getOptionalValue(TENANT_TLS, String.class).isEmpty());
        assertTrue(config.getOptionalValue(ROOT_FILE, String.class).isEmpty());
    }

    private String root() throws IOException {

        final Path file = dir.resolve("ca.pem");
        Files.writeString(file, "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n");
        return file.toString();
    }

    /** The production configuration: this application's properties, the platform's variables as the environment, and the factories the SDK registers. */
    private static SmallRyeConfig config(final Map<String, String> environment) throws IOException {

        final SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder().addDefaultInterceptors().withProfile("prod")
                .withSources(new PropertiesConfigSource(new HashMap<>(environment), "environment", 300))
                .withSources(new PropertiesConfigSource(Path.of("src/main/resources/application.properties").toUri().toURL(), 250));
        ServiceLoader.load(ConfigSourceFactory.class).stream().map(ServiceLoader.Provider::get).filter(InternalRootConfigSourceFactory.class::isInstance)
                .forEach(builder::withSources);
        return builder.build();
    }
}
