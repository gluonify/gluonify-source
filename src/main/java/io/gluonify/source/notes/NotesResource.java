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
 * The notes REST API: the model to copy for your own resource.
 * <ul>
 *   <li>{@code @Path} : the address; {@code @Produces}/{@code @Consumes}: JSON;</li>
 *   <li>{@code @RolesAllowed} : the Charm token roles ("roles" claim); read = {@code source:read}, write = {@code source:write};</li>
 *   <li>{@code @Valid} : the body is validated, 400 otherwise;</li>
 *   <li>{@code @Operation}/{@code @Tag} : OpenAPI documentation, visible in /q/swagger-ui.</li>
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
    @Operation(summary = "The notes, most recent first (200 at most)")
    public List<Note> list() {
        return store.list();
    }

    @GET
    @Path("{id}")
    @RolesAllowed("source:read")
    @Operation(summary = "One note")
    public Response get(@PathParam("id") String id) {
        return store.get(id).map(n -> Response.ok(n).build()).orElseGet(() -> Response.status(404).entity(Map.of("error", "note inconnue")).build());
    }

    @POST
    @RolesAllowed("source:write")
    @Operation(summary = "Creates a note (the author is the token subject)")
    public Response create(@Valid NewNote in, @Context SecurityContext sc, @Context UriInfo uri) {
        String author = sc.getUserPrincipal() == null ? "" : sc.getUserPrincipal().getName();
        Note n = store.create(in, author);
        return Response.created(uri.getAbsolutePathBuilder().path(n.id()).build()).entity(n).build();
    }

    @DELETE
    @Path("{id}")
    @RolesAllowed("source:write")
    @Operation(summary = "Deletes a note")
    public Response delete(@PathParam("id") String id) {
        return store.delete(id) ? Response.noContent().build() : Response.status(404).entity(Map.of("error", "note inconnue")).build();
    }
}
