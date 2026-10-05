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
 * Ce que la plateforme a fourni à cette instance, et comment parler aux AUTRES services. Les variables sont celles que Gluonify ajoute toujours :
 * <ul>
 *   <li>{@code ENV_NAME} : l'environnement (SBX, QUA, ACP, PRD…) ; {@code ENV_NODE} : le rang de la réplique (1, 2…) ; {@code GLUONIFY_SELF_URL} : l'adresse de cette instance ;</li>
 *   <li>{@code SERVICE_<APP>_URL} : l'adresse d'une autre application, présente si vous l'avez déclarée dans {@code "uses"} au déploiement
 *       (c'est aussi ce qui ouvre le réseau : une application isolée ne joint que ce qu'elle déclare).</li>
 * </ul>
 * Aucune valeur secrète n'est jamais renvoyée ici.
 */
@Path("/api/platform")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Plateforme")
public class PlatformResource {
    @Inject
    SourceConfig config;
    @Inject
    NoteStore store;
    @Inject
    WebhookResource webhooks;

    /** Les variables d'environnement : remplaçable dans les tests. */
    Supplier<Map<String, String>> env = System::getenv;
    private volatile HttpClient http;

    @GET
    @RolesAllowed("source:read")
    @Operation(summary = "Environnement fourni par la plateforme, services déclarés, stockage, webhooks reçus")
    public Map<String, Object> info() {
        Map<String, String> e = env.get();
        Map<String, String> services = new TreeMap<>();
        e.forEach((k, v) -> {
            if (k.startsWith("SERVICE_") && k.endsWith("_URL")) services.put(k.substring("SERVICE_".length(), k.length() - "_URL".length()).toLowerCase(Locale.ROOT), v);
        });
        Map<String, Object> out = new TreeMap<>();
        out.put("envName", e.getOrDefault("ENV_NAME", ""));
        out.put("envNode", e.getOrDefault("ENV_NODE", ""));
        out.put("selfUrl", e.getOrDefault("GLUONIFY_SELF_URL", ""));
        out.put("services", services);
        out.put("store", store.kind());
        out.put("storeReady", store.ready());
        out.put("webhooks", webhooks.counters());
        out.put("webhookOpen", config.webhook().key().filter(k -> !k.isBlank()).isPresent());
        return out;
    }

    /**
     * Appelle la santé d'une AUTRE application de la plateforme, par son adresse {@code SERVICE_<APP>_URL}. Exemple minimal d'appel de service à service :
     * l'adresse vient de l'environnement (jamais écrite en dur), seules les applications déclarées dans « uses » sont joignables (liste blanche = les variables SERVICE_*).
     */
    @GET
    @Path("ping/{app}")
    @RolesAllowed("source:read")
    @Operation(summary = "Interroge /q/health/ready d'une application déclarée dans « uses »")
    public Response ping(@PathParam("app") String app) {
        if (!app.matches("[a-z0-9-]{1,63}")) return Response.status(400).entity(Map.of("error", "nom d'application invalide")).build();
        String url = env.get().get("SERVICE_" + app.toUpperCase(Locale.ROOT).replace('-', '_') + "_URL");
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
        if (h == null) http = h = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(); // à la première utilisation (natif)
        return h;
    }
}
