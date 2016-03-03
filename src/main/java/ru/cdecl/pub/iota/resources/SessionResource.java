package ru.cdecl.pub.iota.resources;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.cdecl.pub.iota.main.RestApplication;
import ru.cdecl.pub.iota.models.UserLoginRequest;
import ru.cdecl.pub.iota.models.UserLoginResponse;
import ru.cdecl.pub.iota.models.UserProfile;
import ru.cdecl.pub.iota.models.base.BaseUserIdResponse;
import ru.cdecl.pub.iota.services.AuthenticationService;
import ru.cdecl.pub.iota.services.UserProfileService;

import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@Path("/session")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SessionResource {

    @NotNull
    private final UserProfileService userProfileService;
    @NotNull
    private final AuthenticationService authenticationService;

    public SessionResource(@NotNull UserProfileService userProfileService, @NotNull AuthenticationService authenticationService) {
        this.userProfileService = userProfileService;
        this.authenticationService = authenticationService;
    }

    @GET
    public Response getUserId(@Context HttpServletRequest httpServletRequest) {
        @Nullable final HttpSession httpSession = httpServletRequest.getSession(false);

        if (httpSession == null) {
            return Response.status(Response.Status.FORBIDDEN).entity(RestApplication.EMPTY_RESPONSE).build();
        }

        @Nullable final Object userId = httpSession.getAttribute("user_id");

        if (userId != null && userId instanceof Long) {
            @Nullable final UserProfile userProfile = userProfileService.getUserById((Long) userId);

            if (userProfile != null) {
                return Response.ok(new BaseUserIdResponse(userProfile.getUserId())).build();
            }
        }

        return Response.status(Response.Status.UNAUTHORIZED).entity(RestApplication.EMPTY_RESPONSE).build();
    }

    @PUT
    public Response doLogin(@NotNull UserLoginRequest userLoginRequest, @Context HttpServletRequest httpServletRequest) {
        @NotNull final HttpSession httpSession = httpServletRequest.getSession();

        @Nullable final UserProfile userProfile = userProfileService.getUserByLogin(userLoginRequest.getLogin());
        boolean isPasswordOk = false;

        if (userProfile != null) {
            isPasswordOk = authenticationService.checkPassword(userProfile.getUserId(), userLoginRequest.getPassword());
        }

        if (isPasswordOk) {
            httpSession.setAttribute("user_id", userProfile.getUserId());

            return Response.ok(new UserLoginResponse(userProfile.getUserId())).build();
        }

        return Response.status(Response.Status.BAD_REQUEST).entity(RestApplication.EMPTY_RESPONSE).build();
    }

    @DELETE
    public Response doLogout(@Context HttpServletRequest httpServletRequest) {
        @Nullable final HttpSession httpSession = httpServletRequest.getSession(false);

        if (httpSession == null) {
            return Response.status(Response.Status.FORBIDDEN).entity(RestApplication.EMPTY_RESPONSE).build();
        }

        httpSession.invalidate();

        return Response.ok(RestApplication.EMPTY_RESPONSE).build();
    }

}