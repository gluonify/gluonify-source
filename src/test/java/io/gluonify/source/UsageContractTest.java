package io.gluonify.source;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.gluonify.plasma.usage.ProcessUsage;
import io.gluonify.plasma.usage.UsageContractTests;
import io.gluonify.plasma.usage.UsageContributor;
import io.gluonify.plasma.usage.UsageResource;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestIdentityAssociation;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Source's usage contract {@code gluonify-usage/1} ({@code GET /q/usage}, usage-mcp.md § 3): the five common checks of the SDK ({@link UsageContractTests}), then the
 * readers decided by the component's security (Charm tokens, OIDC roles {@code gluonify:source:usage:read} or {@code gluonify:usage:read}) and
 * {@code attribution: none}. OIDC is disabled in tests: the caller's identity is set per request through {@link TestIdentityAssociation}. The process is read from a
 * fake {@code /proc} (the test workstation may have none): the SDK resource is replaced through {@link QuarkusMock}.
 */
@QuarkusTest
class UsageContractTest extends UsageContractTests {
    static final String PRODUCT_ROLE = "gluonify:source:usage:read";
    /** The bearer sent with an identity: ignored by the tests' security, but the response must never echo a credential. */
    static final String BEARER = "usage-" + HexFormat.of().formatHex(new SecureRandom().generateSeed(16));
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject TestIdentityAssociation identity;

    /** A fake {@code /proc}: 12 s of CPU (1,200 ticks), 8 MiB resident, 4 threads. */
    static Path fakeProc() {
        try {
            Path proc = Files.createTempDirectory("usage-proc");
            proc.toFile().deleteOnExit();
            Files.writeString(proc.resolve("stat"), "1 (java) S 1 1 1 0 -1 0 0 0 0 0 1000 200 0 0 20 0 4 0 1 1 1\n");
            Files.writeString(proc.resolve("status"), "VmRSS:\t8192 kB\n");
            return proc;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void mockProc() {
        List<UsageContributor> contributors = List.of();
        QuarkusMock.installMockForType(new UsageResource(new ProcessUsage(fakeProc(), Path.of("/nonexistent"), 100, System::currentTimeMillis, 0, () -> 1),
                System::currentTimeMillis, () -> contributors, "source", "0.0.0-test", null, "n-test"), UsageResource.class);
    }

    @AfterEach
    void anonymousAgain() {
        identity.setTestIdentity(null);
    }

    /** {@code GET /q/usage} as a caller holding {@code roles}; {@code null}: anonymous, without any token. */
    Response get(Set<String> roles) {
        if (roles == null) {
            identity.setTestIdentity(null);
            return given().when().get("/q/usage").andReturn();
        }
        identity.setTestIdentity(QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal("usage-reader")).addRoles(roles).build());
        return given().header("Authorization", "Bearer " + BEARER).when().get("/q/usage").andReturn();
    }

    @Override
    protected byte[] authorizedBody() {
        Response r = get(Set.of(PRODUCT_ROLE));
        assertEquals(200, r.statusCode());
        return r.asByteArray();
    }

    @Override
    protected int statusWithoutToken() {
        return get(null).statusCode();
    }

    @Override
    protected int statusWithoutRole() {
        return get(Set.of("gluonify:source:other")).statusCode();
    }

    /** The bearer the tests send (the application has no other secret in its test configuration). */
    @Override
    protected Collection<String> secrets() {
        return List.of(BEARER);
    }

    @Test
    void commonRoleIsAcceptedAndTheResponseIsNotCached() {
        assertEquals(200, get(Set.of(UsageResource.ROLE)).statusCode(), "gluonify:usage:read (the one Higgs gets)");
        assertEquals("no-store", get(Set.of(PRODUCT_ROLE)).header("Cache-Control"));
    }

    @Test
    void processOfTheComponentWithoutAttribution() {
        JsonNode body = JSON.readTree(new String(authorizedBody(), StandardCharsets.UTF_8));
        assertEquals("source", body.get("component").asString());
        assertEquals("none", body.get("attribution").asString());
        assertEquals(0, body.get("byApp").size());
        assertEquals(12.0, body.get("process").get("cpuSecondsTotal").asDouble(), 1e-9);
        assertEquals(8L << 20, body.get("process").get("memoryBytes").asLong());
    }
}
