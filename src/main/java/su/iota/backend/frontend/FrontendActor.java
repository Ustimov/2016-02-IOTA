package su.iota.backend.frontend;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.ExitMessage;
import co.paralleluniverse.actors.LifecycleMessage;
import co.paralleluniverse.actors.behaviors.Server;
import co.paralleluniverse.comsat.webactors.*;
import co.paralleluniverse.fibers.SuspendExecution;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;
import su.iota.backend.messages.game.AbstractPlayerActionMessage;
import su.iota.backend.messages.game.impl.GameStateMessage;
import su.iota.backend.messages.game.impl.PlayerPassCardMessage;
import su.iota.backend.messages.game.impl.PlayerPingMessage;
import su.iota.backend.messages.game.impl.PlayerPlaceCardMessage;
import su.iota.backend.misc.ServiceUtils;
import su.iota.backend.misc.gson.RuntimeTypeAdapterFactory;
import su.iota.backend.models.UserProfile;
import su.iota.backend.settings.SettingsService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static co.paralleluniverse.comsat.webactors.HttpResponse.error;
import static co.paralleluniverse.comsat.webactors.HttpResponse.ok;
import static org.apache.http.HttpStatus.*;

@WebActor(httpUrlPatterns = {"/session", "/user/*", "/game", "/highscore"}, webSocketUrlPatterns = {"/ws"})
public final class FrontendActor extends BasicActor<Object, Void> {

    private boolean isInitialized = false;
    private String contextPath;
    private FrontendService frontendService;
    private int receiveTimeout;
    private final Set<ActorRef<WebMessage>> webSockets = new HashSet<>();
    private Object gameSessionWatch;

    private void init() throws InterruptedException, SuspendExecution {
        final SettingsService settingsService = ServiceUtils.getService(SettingsService.class);
        contextPath = settingsService.getServerContextPathSetting();
        receiveTimeout = settingsService.getFrontendReceiveTimeoutSeconds();
        frontendService = ServiceUtils.getService(FrontendService.class);
    }

    @Override
    protected Void doRun() throws SuspendExecution, InterruptedException {
        if (!isInitialized) {
            init();
            isInitialized = true;
        }
        //noinspection InfiniteLoopStatement
        while (true) {
            final Object message = receive(receiveTimeout, TimeUnit.SECONDS);
            if (message instanceof WebMessage) {
                handleWebMessage((WebMessage) message);
            } else if (message instanceof OutgoingMessage) {
                handleOutgoingMessage(message);
            } else if (message instanceof Server) {
                //noinspection unchecked
                final Server<IncomingMessage, OutgoingMessage, Object> gameSession =
                        (Server<IncomingMessage, OutgoingMessage, Object>) message;
                gameSessionWatch = watch(gameSession);
                frontendService.setGameSession(self(), gameSession);
            } else if (message instanceof ExitMessage) {
                handleExitMessage((ExitMessage) message);
            }
            checkCodeSwap();
        }
    }

    private void handleOutgoingMessage(Object message) throws SuspendExecution, InterruptedException {
        final WebMessage jsonMessage = new WebDataMessage(self(), getGson().toJson(message));
        for (ActorRef<WebMessage> webSocket : webSockets) {
            webSocket.send(jsonMessage);
        }
    }

    private void handleExitMessage(ExitMessage exitMessage) throws SuspendExecution {
        final ActorRef dyingActor = exitMessage.getActor();
        if (webSockets.contains(dyingActor)) {
            webSockets.remove(dyingActor);
            if (webSockets.isEmpty()) {
                frontendService.softDropPlayer(self());
            }
        } else if (exitMessage.getWatch().equals(gameSessionWatch)) {
            gameSessionWatch = null;
            frontendService.resetGameSession();
        }
    }

    private void handleWebMessage(WebMessage message) throws SuspendExecution, InterruptedException {
        if (message instanceof HttpRequest) {
            final HttpRequest httpRequest = (HttpRequest) message;
            routeHttpRequest(httpRequest);
        } else if (message instanceof WebSocketOpened) {
            //noinspection unchecked
            final ActorRef<WebMessage> webSocketActor = (ActorRef<WebMessage>) message.getFrom();
            watch(webSocketActor);
            webSockets.add(webSocketActor);
        }
    }

    @Override
    protected Object handleLifecycleMessage(LifecycleMessage m) {
        if (m instanceof ExitMessage) {
            return m;
        }
        return super.handleLifecycleMessage(m);
    }

    private void routeHttpRequest(HttpRequest httpRequest) throws SuspendExecution, InterruptedException {
        final String resourceUri = getResourceUri(httpRequest);
        if (resourceUri.startsWith("/session")) {
            handleHttpSessionRequest(httpRequest);
        } else if (resourceUri.startsWith("/user/")) {
            handleHttpConcreteUserRequest(httpRequest);
        } else if (resourceUri.startsWith("/user")) {
            handleHttpUserRequest(httpRequest);
        } else if (resourceUri.startsWith("/game")) {
            handleHttpGameRequest(httpRequest);
        } else if (resourceUri.startsWith("/highscore")) {
            handleHttpHighscoreRequest(httpRequest);
        } else {
            throw new AssertionError(resourceUri);
        }
    }

    private void handleHttpGameRequest(HttpRequest httpRequest) throws SuspendExecution, InterruptedException {
        if (httpRequest.getMethod().equals("GET")) {
            final JsonObject response = new JsonObject();
            response.addProperty("__ok", frontendService.askGameStateUpdate(self()));
            respondWithJson(httpRequest, response);
        } else if (httpRequest.getMethod().equals("POST")) {
            //noinspection OverlyBroadCatchBlock
            try {
                final Gson gson = getGsonForPlayerActionMessages();
                final AbstractPlayerActionMessage actionMessage = gson.fromJson(httpRequest.getStringBody(), AbstractPlayerActionMessage.class);
                if (actionMessage != null) {
                    actionMessage.setFrom(self());
                    respondWithJson(httpRequest, frontendService.performPlayerAction(actionMessage));
                    if (actionMessage.isGoodbyeMessage()) {
                        frontendService.dropPlayer(self());
                    }
                    return;
                }
            } catch (JsonParseException ignored) {
            }
            respondWithError(httpRequest, SC_BAD_REQUEST);
        } else {
            respondWithError(httpRequest, SC_METHOD_NOT_ALLOWED);
        }
    }

    private void handleHttpSessionRequest(HttpRequest httpRequest) throws SuspendExecution {
        final JsonObject jsonObject = new JsonObject();
        switch (httpRequest.getMethod()) {
            case "GET": {
                handleHttpBusinessSessionGet(httpRequest, jsonObject);
                break;
            }
            case "PUT": {
                handleHttpBusinessSessionPut(httpRequest, jsonObject);
                break;
            }
            case "DELETE":
                handleHttpBusinessSessionDelete(httpRequest, jsonObject);
                break;
            default:
                respondWithError(httpRequest, SC_METHOD_NOT_ALLOWED);
                break;
        }
    }

    private void handleHttpBusinessSessionGet(HttpRequest httpRequest, JsonObject jsonObject) throws SuspendExecution {
        final UserProfile signedInUser = frontendService.getSignedInUser();
        if (signedInUser != null) {
            jsonObject.addProperty("id", signedInUser.getId());
            jsonObject.addProperty("ref", System.identityHashCode(self()));
        }
        jsonObject.addProperty("__ok", signedInUser != null);
        respondWithJson(httpRequest, jsonObject);
    }

    private void handleHttpBusinessSessionPut(HttpRequest httpRequest, JsonObject jsonObject) throws SuspendExecution {
        try {
            final UserProfile userProfile = new Gson().fromJson(httpRequest.getStringBody(), UserProfile.class);
            final boolean isSignedIn = frontendService.signIn(userProfile);
            if (isSignedIn) {
                final UserProfile signedInUser = frontendService.getSignedInUser();
                if (signedInUser == null) {
                    throw new AssertionError();
                }
                jsonObject.addProperty("id", frontendService.getSignedInUser().getId());
                jsonObject.addProperty("ref", System.identityHashCode(self()));
            }
            jsonObject.addProperty("__ok", isSignedIn);
            respondWithJson(httpRequest, jsonObject);
        } catch (JsonSyntaxException ex) {
            respondWithError(httpRequest, SC_BAD_REQUEST, ex);
        }
    }

    private void handleHttpBusinessSessionDelete(HttpRequest httpRequest, JsonObject jsonObject) throws SuspendExecution {
        frontendService.signOut();
        jsonObject.addProperty("__ok", true);
        respondWithJson(httpRequest, jsonObject);
    }

    private void handleHttpUserRequest(HttpRequest httpRequest) throws SuspendExecution {
        if (!httpRequest.getMethod().equals("PUT")) {
            respondWithError(httpRequest, SC_METHOD_NOT_ALLOWED);
            return;
        }
        final JsonObject jsonObject = new JsonObject();
        try {
            final UserProfile userProfile = new Gson().fromJson(httpRequest.getStringBody(), UserProfile.class);
            final boolean isSignedUp = frontendService.signUp(userProfile);
            if (isSignedUp) {
                jsonObject.addProperty("id", userProfile.getId());
            }
            jsonObject.addProperty("__ok", isSignedUp);
            respondWithJson(httpRequest, jsonObject);
        } catch (JsonSyntaxException ex) {
            respondWithError(httpRequest, SC_BAD_REQUEST, ex);
        }
    }

    private void handleHttpConcreteUserRequest(HttpRequest httpRequest) throws SuspendExecution {
        final Long userId = getUserIdFromResourceUri(getResourceUri(httpRequest));
        if (userId == null) {
            respondWithError(httpRequest, SC_BAD_REQUEST, new AssertionError());
            return;
        }
        final JsonObject jsonObject = new JsonObject();
        switch (httpRequest.getMethod()) {
            case "GET":
                handleHttpConcreteUserGet(httpRequest, jsonObject, userId);
                break;
            default:
                respondWithError(httpRequest, SC_METHOD_NOT_ALLOWED);
                break;
        }
    }

    private void handleHttpConcreteUserGet(HttpRequest httpRequest, JsonObject jsonObject, Long userId) throws SuspendExecution {
        try {
            final UserProfile userProfile = frontendService.getUserById(userId);
            final JsonElement jsonResponse = getGson().toJsonTree(userProfile);
            final boolean isOk = jsonResponse != null && jsonResponse.isJsonObject();
            if (isOk) {
                for (Map.Entry<String, JsonElement> property : jsonResponse.getAsJsonObject().entrySet()) {
                    jsonObject.add(property.getKey(), property.getValue());
                }
            }
            jsonObject.addProperty("__ok", isOk);
            respondWithJson(httpRequest, jsonObject);
        } catch (JsonSyntaxException ex) {
            respondWithError(httpRequest, SC_BAD_REQUEST, ex);
        }
    }

    private void handleHttpHighscoreRequest(HttpRequest httpRequest) throws SuspendExecution {
        if (!httpRequest.getMethod().equals("GET")) {
            respondWithError(httpRequest, SC_METHOD_NOT_ALLOWED);
            return;
        }
        respondWithError(httpRequest, SC_NOT_IMPLEMENTED); // todo
    }

    private Gson getGson() {
        return defaultGsonBuilderFunction(new GsonBuilder());
    }

    private Gson defaultGsonBuilderFunction(GsonBuilder gsonBuilder) {
        return gsonBuilder
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(GameStateMessage.class, new GameStateMessage.Serializer())
                .create();
    }

    private Gson getGsonForPlayerActionMessages() {
        return new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapterFactory(RuntimeTypeAdapterFactory.of(AbstractPlayerActionMessage.class, "__type")
                        .registerSubtype(PlayerPingMessage.class)
                        .registerSubtype(PlayerPlaceCardMessage.class)
                        .registerSubtype(PlayerPassCardMessage.class)
                ).create();
    }

    private void respondWithJson(HttpRequest httpRequest, @Nullable Object object) throws SuspendExecution {
        respondWithJson(httpRequest, object, this::defaultGsonBuilderFunction);
    }

    private void respondWithJson(HttpRequest httpRequest, @Nullable Object object, Function<GsonBuilder, Gson> gsonFunction) throws SuspendExecution {
        final Gson gson = gsonFunction.apply(new GsonBuilder());
        httpRequest.getFrom().send(ok(self(), httpRequest, gson.toJson(object)).setContentType("application/json").build());
    }

    private void respondWithError(HttpRequest httpRequest, int code) throws SuspendExecution {
        respondWithError(httpRequest, code, null);
    }

    private void respondWithError(HttpRequest httpRequest, int code, @Nullable Throwable cause) throws SuspendExecution {
        httpRequest.getFrom().send(error(self(), httpRequest, code, cause).build());
    }

    @NotNull
    private String getResourceUri(HttpRequest httpRequest) {
        if (!isInitialized) {
            throw new IllegalStateException();
        }
        return httpRequest.getRequestURI().substring(contextPath.length());
    }

    @Nullable
    private Long getUserIdFromResourceUri(String resourceUri) {
        try {
            return Long.parseLong(resourceUri.substring(resourceUri.lastIndexOf('/') + 1));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

}
