package io.gluonify.source.platform;

import io.gluonify.source.SourceConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook receiver for <b>Photon</b> (Gluonify's API gateway). Photon receives your partners' messages (signature verified, XML/SOAP/form converted to
 * JSON), then DELIVERS them to this address. The delivery contract is simple and must be respected:
 * <ol>
 *   <li><b>A 2XX code acknowledges</b>: Photon considers the message delivered. Any other code (or a failure) makes it <b>replay</b> according to the webhook policy;</li>
 *   <li>so <b>answer quickly</b> (long processing happens afterwards) and <b>be idempotent</b>: "at least once" means the same message can arrive twice
 *       (a lost acknowledgement, a leader failover). The {@code X-Gluonify-Event-Id} header is the deduplication key; {@code X-Gluonify-Delivery-Attempt} counts the attempts;</li>
 *   <li>Photon does not send a Charm token: protect this entry point with a key that you configure in the webhook target
 *       ({@code "headers": {"X-Api-Key": "..."}}) and here ({@code source.webhook.key}, coming from the vault). Without a configured key, the receiver is closed (404).</li>
 * </ol>
 * This example just counts the received events (visible in /api/platform); replace the body of {@link #receive} with your own processing.
 */
@Path("/hooks/events")
@Tag(name = "Webhooks")
public class WebhookResource {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_REMEMBERED = 10_000;

    @Inject
    SourceConfig config;

    private final ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong accepted = new java.util.concurrent.atomic.AtomicLong(), duplicates = new java.util.concurrent.atomic.AtomicLong();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Receives a delivery from Photon (acknowledges with 200; idempotent on X-Gluonify-Event-Id)")
    public Response receive(@HeaderParam("X-Api-Key") String key, @HeaderParam("X-Gluonify-Event-Id") String eventId, @HeaderParam("X-Gluonify-Webhook-Id") String webhook,
            @HeaderParam("X-Gluonify-Delivery-Attempt") String attempt, String body) {
        String expected = config.webhook().key().orElse("");
        if (expected.isBlank()) return Response.status(404).build(); // receiver closed as long as no key is configured
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) return Response.status(401).entity(Map.of("error", "clé invalide")).build();
        if (eventId == null || eventId.isBlank()) return Response.status(400).entity(Map.of("error", "X-Gluonify-Event-Id manquant")).build();
        JsonNode json;
        try {
            json = MAPPER.readTree(body);
        } catch (RuntimeException e) {
            // an unreadable body will not fix itself by replaying: 4XX (Photon will replay it according to its policy, then dead-letter it)
            return Response.status(400).entity(Map.of("error", "JSON illisible")).build();
        }
        if (seen.size() > MAX_REMEMBERED) seen.clear(); // bounded memory (a real service keeps these identifiers in its database)
        if (seen.putIfAbsent(eventId, Boolean.TRUE) != null) {
            duplicates.incrementAndGet();
            return Response.ok(Map.of("status", "duplicate", "eventId", eventId)).build(); // already processed: we still acknowledge (2XX), without redoing the work
        }
        accepted.incrementAndGet();
        // HERE: your processing. It receives "json" (the message ALREADY converted to JSON by Photon).
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "accepted");
        out.put("eventId", eventId);
        out.put("webhook", webhook);
        out.put("attempt", attempt);
        out.put("fields", json.size());
        return Response.ok(out).build();
    }

    /** For /api/platform. */
    public Map<String, Long> counters() {
        return Map.of("accepted", accepted.get(), "duplicates", duplicates.get());
    }
}
