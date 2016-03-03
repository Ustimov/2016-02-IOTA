package ru.cdecl.pub.iota.endpoints;

import ru.cdecl.pub.iota.helpers.Status;
import ru.cdecl.pub.iota.models.UserLoginRequest;
import ru.cdecl.pub.iota.models.UserLoginResponse;
import ru.cdecl.pub.iota.models.UserProfile;
import ru.cdecl.pub.iota.models.base.BaseApiResponse;
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
public class SessionEndpoint {

    private final UserProfileService userProfileService;
    private final AuthenticationService authenticationService;

    public SessionEndpoint(UserProfileService userProfileService, AuthenticationService authenticationService) {
        this.userProfileService = userProfileService;
        this.authenticationService = authenticationService;
    }

    @GET
    public Response getUserId(@Context HttpServletRequest httpServletRequest) {
        final HttpSession httpSession = httpServletRequest.getSession(false);

        if (httpSession != null) {
            synchronized (httpSession.getId().intern()) {
                try {
                    final Object userId = httpSession.getAttribute("user_id");

                    if (userId != null && userId instanceof Long) {
                        final UserProfile userProfile = userProfileService.getUserById((Long) userId);

                        if (userProfile != null) {
                            return Response.ok(new BaseUserIdResponse(userProfile.getUserId())).build();
                        }
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        }

        return Response.status(Response.Status.UNAUTHORIZED).entity(new BaseApiResponse(Status.UNAUTHORIZED,
                Status.UNAUTHORIZED_MSG)).build();
    }

    @PUT
    public Response doLogin(UserLoginRequest userLoginRequest, @Context HttpServletRequest httpServletRequest) {
        final HttpSession httpSession = httpServletRequest.getSession();
        // TODO: Check input data
        final UserProfile userProfile = userProfileService.getUserByLogin(userLoginRequest.getLogin());
        boolean isPasswordOk = false;

        if (userProfile != null) {
            isPasswordOk = authenticationService.checkPassword(userProfile.getUserId(), userLoginRequest.getPassword().toCharArray());
        }

        if (isPasswordOk) {
            synchronized (httpSession.getId().intern()) {
                try {
                    httpSession.setAttribute("user_id", userProfile.getUserId());

                    return Response.ok(new UserLoginResponse(userProfile.getUserId(), Status.OK, Status.OK_MSG)).build();
                } catch (IllegalStateException ignored) {
                }
            }
        }

        return Response.status(Response.Status.OK).entity(new BaseApiResponse(
                Status.WRONG_LOGIN_OR_PASSWORD, Status.WRONG_LOGIN_OR_PASSWORD_MSG)).build();
    }

    @DELETE
    public Response doLogout(@Context HttpServletRequest httpServletRequest) {
        final HttpSession httpSession = httpServletRequest.getSession(false);

        if (httpSession != null) {
            synchronized (httpSession.getId().intern()) {
                try {
                    httpSession.invalidate();
                } catch (IllegalStateException ignored) {
                }
            }
        }

        return Response.ok(new BaseApiResponse(Status.OK, Status.OK_MSG)).build();
    }

}
