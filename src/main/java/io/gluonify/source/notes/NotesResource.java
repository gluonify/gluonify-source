package io.gluonify.source.notes;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * L'API REST des notes : le modèle à copier pour votre propre ressource.
 * <ul>
 *   <li>{@code @Path} : l'adresse ; {@code @Produces}/{@code @Consumes} : JSON ;</li>
 *   <li>{@code @RolesAllowed} : les rôles du jeton Charm (claim « roles ») ; lecture = {@code source:read}, écriture = {@code source:write} ;</li>
 *   <li>{@code @Valid} : le corps est validé, 400 sinon ;</li>
 *   <li>{@code @Operation}/{@code @Tag} : documentation OpenAPI, visible dans /q/swagger-ui.</li>
 * </ul>
 */
@Path("/api/notes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Notes")
public class NotesResource {
    @Inject
    NoteStore store;

    @GET
    @RolesAllowed("source:read")
    @Operation(summary = "Les notes, les plus récentes d'abord (200 au plus)")
    public List<Note> list() {
        return store.list();
    }

    @GET
    @Path("{id}")
    @RolesAllowed("source:read")
    @Operation(summary = "Une note")
    public Response get(@PathParam("id") String id) {
        return store.get(id).map(n -> Response.ok(n).build()).orElseGet(() -> Response.status(404).entity(Map.of("error", "note inconnue")).build());
    }

    @POST
    @RolesAllowed("source:write")
    @Operation(summary = "Crée une note (l'auteur est le sujet du jeton)")
    public Response create(@Valid NewNote in, @Context SecurityContext sc, @Context UriInfo uri) {
        String author = sc.getUserPrincipal() == null ? "" : sc.getUserPrincipal().getName();
        Note n = store.create(in, author);
        return Response.created(uri.getAbsolutePathBuilder().path(n.id()).build()).entity(n).build();
    }

    @DELETE
    @Path("{id}")
    @RolesAllowed("source:write")
    @Operation(summary = "Supprime une note")
    public Response delete(@PathParam("id") String id) {
        return store.delete(id) ? Response.noContent().build() : Response.status(404).entity(Map.of("error", "note inconnue")).build();
    }
}
