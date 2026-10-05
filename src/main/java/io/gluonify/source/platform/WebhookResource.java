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
 * Récepteur de webhooks de <b>Photon</b> (la passerelle d'API de Gluonify). Photon reçoit les messages de vos partenaires (signature vérifiée, conversion XML/SOAP/formulaire en
 * JSON), puis les LIVRE à cette adresse. Le contrat de livraison est simple et il faut le respecter :
 * <ol>
 *   <li><b>Un code 2XX acquitte</b> : Photon considère le message livré. Tout autre code (ou une panne) le fait <b>rejouer</b> selon la politique du webhook ;</li>
 *   <li>donc <b>répondez vite</b> (le traitement long se fait après) et <b>soyez idempotent</b> : « au moins une fois » signifie qu'un même message peut arriver deux fois
 *       (un acquittement perdu, un basculement de meneur). L'en-tête {@code X-Gluonify-Event-Id} est la clé de déduplication ; {@code X-Gluonify-Delivery-Attempt} compte les essais ;</li>
 *   <li>Photon n'envoie pas de jeton Charm : protégez ce point d'entrée par une clé que vous configurez dans la cible du webhook
 *       ({@code "headers": {"X-Api-Key": "…"}}) et ici ({@code source.webhook.key}, venant du coffre). Sans clé configurée, le récepteur est fermé (404).</li>
 * </ol>
 * Cet exemple se contente de compter les événements reçus (visible dans /api/platform) ; remplacez le corps de {@link #receive} par votre traitement.
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
    @Operation(summary = "Reçoit une livraison de Photon (acquitte en 200 ; idempotent sur X-Gluonify-Event-Id)")
    public Response receive(@HeaderParam("X-Api-Key") String key, @HeaderParam("X-Gluonify-Event-Id") String eventId, @HeaderParam("X-Gluonify-Webhook-Id") String webhook,
            @HeaderParam("X-Gluonify-Delivery-Attempt") String attempt, String body) {
        String expected = config.webhook().key().orElse("");
        if (expected.isBlank()) return Response.status(404).build(); // récepteur fermé tant qu'aucune clé n'est configurée
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) return Response.status(401).entity(Map.of("error", "clé invalide")).build();
        if (eventId == null || eventId.isBlank()) return Response.status(400).entity(Map.of("error", "X-Gluonify-Event-Id manquant")).build();
        JsonNode json;
        try {
            json = MAPPER.readTree(body);
        } catch (RuntimeException e) {
            // un corps illisible ne s'arrangera pas en rejouant : 4XX (Photon le rejouera selon sa politique, puis le mettra en file morte)
            return Response.status(400).entity(Map.of("error", "JSON illisible")).build();
        }
        if (seen.size() > MAX_REMEMBERED) seen.clear(); // mémoire bornée (un vrai service garde ces identifiants dans sa base)
        if (seen.putIfAbsent(eventId, Boolean.TRUE) != null) {
            duplicates.incrementAndGet();
            return Response.ok(Map.of("status", "duplicate", "eventId", eventId)).build(); // déjà traité : on acquitte quand même (2XX), sans refaire le travail
        }
        accepted.incrementAndGet();
        // ICI : votre traitement. Il reçoit « json » (le message DÉJÀ converti en JSON par Photon).
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "accepted");
        out.put("eventId", eventId);
        out.put("webhook", webhook);
        out.put("attempt", attempt);
        out.put("fields", json.size());
        return Response.ok(out).build();
    }

    /** Pour /api/platform. */
    public Map<String, Long> counters() {
        return Map.of("accepted", accepted.get(), "duplicates", duplicates.get());
    }
}
