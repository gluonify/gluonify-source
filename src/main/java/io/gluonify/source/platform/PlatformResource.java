package io.gluonify.source.platform;

import io.gluonify.source.SourceConfig;
import io.gluonify.source.notes.NoteStore;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * What the platform provided to this instance, and how to talk to OTHER services. The variables are the ones Gluonify always adds:
 * <ul>
 *   <li>{@code GLUONIFY_ENVIRONMENT} : the environment (SBX, QUA, ACP, PRD...); {@code GLUONIFY_REPLICA}: the replica rank (1, 2...); {@code GLUONIFY_SELF_URL}: the address of this instance;</li>
 *   <li>{@code GLUONIFY_SERVICE_<APP>_URL} : the address of another application, present if you declared it in {@code "uses"} at deployment
 *       (this is also what opens the network: an isolated application only reaches what it declares).</li>
 * </ul>
 * No secret value is ever returned here.
 */
@Path("/api/platform")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Platform")
public class PlatformResource {
    @Inject
    SourceConfig config;
    @Inject
    NoteStore store;
    @Inject
    WebhookResource webhooks;

    /** The environment variables: replaceable in tests. */
    Supplier<Map<String, String>> env = System::getenv;
    private volatile HttpClient http;

    @GET
    @RolesAllowed("source:read")
    @Operation(summary = "Environment provided by the platform, declared services, storage, received webhooks")
    public Map<String, Object> info() {
        Map<String, String> e = env.get();
        Map<String, String> services = new TreeMap<>();
        e.forEach((k, v) -> {
            if (k.startsWith("GLUONIFY_SERVICE_") && k.endsWith("_URL")) services.put(k.substring("GLUONIFY_SERVICE_".length(), k.length() - "_URL".length()).toLowerCase(Locale.ROOT), v);
        });
        Map<String, Object> out = new TreeMap<>();
        out.put("envName", e.getOrDefault("GLUONIFY_ENVIRONMENT", ""));
        out.put("envNode", e.getOrDefault("GLUONIFY_REPLICA", ""));
        out.put("selfUrl", e.getOrDefault("GLUONIFY_SELF_URL", ""));
        out.put("services", services);
        out.put("store", store.kind());
        out.put("storeReady", store.ready());
        out.put("webhooks", webhooks.counters());
        out.put("webhookOpen", config.webhook().key().filter(k -> !k.isBlank()).isPresent());
        return out;
    }

    /**
     * Calls the health of ANOTHER platform application, through its {@code GLUONIFY_SERVICE_<APP>_URL} address. Minimal example of a service-to-service call:
     * the address comes from the environment (never hard-coded), only applications declared in "uses" are reachable (allow-list = the SERVICE_* variables).
     */
    @GET
    @Path("ping/{app}")
    @RolesAllowed("source:read")
    @Operation(summary = "Queries /q/health/ready of an application declared in \"uses\"")
    public Response ping(@PathParam("app") String app) {
        if (!app.matches("[a-z0-9-]{1,63}")) return Response.status(400).entity(Map.of("error", "nom d'application invalide")).build();
        String url = env.get().get("GLUONIFY_SERVICE_" + app.toUpperCase(Locale.ROOT).replace('-', '_') + "_URL");
        if (url == null) return Response.status(404).entity(Map.of("error", "application non déclarée dans « uses » : " + app)).build();
        try {
            HttpResponse<String> r = client().send(HttpRequest.newBuilder(URI.create(url + "/q/health/ready")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return Response.ok(Map.of("app", app, "status", r.statusCode())).build();
        } catch (java.io.IOException e) {
            return Response.status(502).entity(Map.of("error", "injoignable : " + e.getMessage())).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(504).build();
        }
    }

    private HttpClient client() {
        HttpClient h = http;
        if (h == null) http = h = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(); // on first use (native)
        return h;
    }
}
