package su.iota.backend.game;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.ServerActor;
import co.paralleluniverse.fibers.SuspendExecution;
import com.esotericsoftware.minlog.Log;
import org.glassfish.hk2.api.PerLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;
import su.iota.backend.messages.game.AbstractPlayerActionMessage;
import su.iota.backend.messages.game.impl.*;
import su.iota.backend.messages.internal.GameSessionDropPlayerMessage;
import su.iota.backend.messages.internal.GameSessionInitMessage;
import su.iota.backend.models.UserProfile;
import su.iota.backend.models.game.Coordinate;
import su.iota.backend.models.game.FieldItem;

import javax.inject.Inject;
import java.util.*;

@Service
@PerLookup
public final class GameSessionActor extends ServerActor<IncomingMessage, OutgoingMessage, Object> {

    @Inject
    GameMechanics gameMechanics;

    private Map<ActorRef<Object>, UserProfile> players;

    @Nullable
    @Override
    protected OutgoingMessage handleCall(ActorRef<?> from, Object id, IncomingMessage message) throws Exception, SuspendExecution {
        if (message instanceof GameSessionInitMessage) {
            final GameSessionInitMessage initMessage = ((GameSessionInitMessage) message);
            if (players != null) {
                return new GameSessionInitMessage.Result(false);
            } else {
                players = initMessage.getPlayers();
                for (final ActorRef<Object> frontend : players.keySet()) {
                    watch(frontend);
                    gameMechanics.addPlayer(getGameKeyForPlayer(frontend));
                }
                gameMechanics.initialize();
                return new GameSessionInitMessage.Result(true);
            }
        } else if (message instanceof AbstractPlayerActionMessage) {
            final AbstractPlayerActionMessage actionMessage = (AbstractPlayerActionMessage) message;
            //noinspection unchecked
            final ActorRef<Object> frontend = (ActorRef<Object>) actionMessage.getFrom();
            final AbstractPlayerActionMessage.AbstractResultMessage resultMessage
                    = handlePlayerActionMessage(actionMessage);
            if (actionMessage.isEndSequenceTrigger() && frontend != null) {
                gameMechanics.endTurn(getGameKeyForPlayer(frontend));
                resultMessage.setBroadcastTrigger(true);
            }
            if (resultMessage.isBroadcastTrigger()) {
                broadcastGameState();
            }
            return resultMessage;
        } else {
            return super.handleCall(from, id, message);
        }
    }

    @Override
    protected void handleCast(ActorRef<?> from, Object id, Object message) throws SuspendExecution {
        if (message instanceof GameSessionDropPlayerMessage) {
            final GameSessionDropPlayerMessage dropMessage = (GameSessionDropPlayerMessage) message;
            final ActorRef<Object> player = dropMessage.getPlayer();
            if (players.containsKey(player)) {
                players.remove(player);
                gameMechanics.dropPlayer(getGameKeyForPlayer(player));
                Log.info("Dropping player from game! " + player.toString());
                broadcastGameState();
            }
            if (players.isEmpty()) {
                Log.info("Last player disconnected, shutting down! " + self().toString());
                shutdown();
            }
        } else if (message instanceof ActorRef<?>) {
            //noinspection unchecked
            final ActorRef<Object> frontend = (ActorRef<Object>) message;
            final GameStateMessage gameStateMessage = buildGameStateMessage();
            gameStateMessage.filterFor(getGameKeyForPlayer(frontend));
            frontend.send(gameStateMessage);
        } else {
            super.handleCast(from, id, message);
        }
    }

    @NotNull
    private AbstractPlayerActionMessage.AbstractResultMessage handlePlayerActionMessage(AbstractPlayerActionMessage message) throws SuspendExecution {
        //noinspection unchecked
        final ActorRef<Object> frontend = (ActorRef<Object>) message.getFrom();
        if (frontend == null || !players.containsKey(frontend)) {
            throw new AssertionError();
        }
        if (message instanceof PlayerPlaceCardMessage) {
            return handlePlaceCardMessage((PlayerPlaceCardMessage) message, frontend);
        } else if (message instanceof PlayerPassCardMessage) {
            return handlePassCardMessage((PlayerPassCardMessage) message, frontend);
        } else if (message instanceof PlayerPingMessage) {
            // -- todo: remove after debugging ------------
            if (((PlayerPingMessage) message).isDebugConclude()) {
                gameMechanics.setConcluded(true);
                //noinspection AnonymousInnerClassMayBeStatic
                return new PlayerPingMessage.ResultMessage() {{
                    broadcast = true;
                }};
            }
            // --------------------------------------------
            return new PlayerPingMessage.ResultMessage();
        }
        return new IllegalPlayerActionResultMessage();
    }

    @NotNull
    private PlayerPlaceCardMessage.ResultMessage handlePlaceCardMessage(@NotNull PlayerPlaceCardMessage message, @NotNull ActorRef<Object> frontend) throws SuspendExecution {
        final UUID uuid = message.getUuid();
        final Coordinate coordinate = message.getCoordinate();
        if (uuid == null || coordinate == null) {
            return new PlayerPlaceCardMessage.ResultMessage(false);
        }
        final int playerKey = getGameKeyForPlayer(frontend);
        final boolean isOk = message.isEphemeral()
                ? gameMechanics.tryEphemeralPlayCard(playerKey, coordinate, uuid)
                : gameMechanics.tryPlayCard(playerKey, coordinate, uuid);
        if (message.isEndSequenceTrigger()) {
            gameMechanics.endTurn(playerKey);
        }
        return new PlayerPlaceCardMessage.ResultMessage(isOk);
    }

    @NotNull
    private PlayerPassCardMessage.ResultMessage handlePassCardMessage(@NotNull PlayerPassCardMessage message, @NotNull ActorRef<Object> frontend) throws SuspendExecution {
        final int playerRef = getGameKeyForPlayer(frontend);
        final UUID uuid = message.getUuid();
        boolean isOk = uuid != null;
        if (isOk) {
            isOk = message.isEphemeral()
                    ? gameMechanics.tryEphemeralPassCard(playerRef, uuid)
                    : gameMechanics.tryPassCard(playerRef, uuid);
        }
        return new PlayerPassCardMessage.ResultMessage(isOk);
    }

    private void broadcastGameState() throws SuspendExecution {
        for (ActorRef<Object> playerFrontend : players.keySet()) {
            final GameStateMessage gameStateMessage = buildGameStateMessage();
            gameStateMessage.filterFor(getGameKeyForPlayer(playerFrontend));
            playerFrontend.send(gameStateMessage);
        }
    }

    private int getGameKeyForPlayer(@NotNull ActorRef<Object> frontend) throws SuspendExecution {
        return System.identityHashCode(frontend);
    }

    private GameStateMessage buildGameStateMessage() throws SuspendExecution {
        final GameStateMessage gameStateMessage = new GameStateMessage();
        gameStateMessage.setFrom(self());
        gameStateMessage.setUuid(gameMechanics.getCurrentGameStateUuid());
        gameStateMessage.setPlayerRef(gameMechanics.getCurrentPlayer());
        gameStateMessage.setConcluded(gameMechanics.isConcluded());
        for (Map.Entry<ActorRef<Object>, UserProfile> player : players.entrySet()) {
            final ActorRef<Object> playerFrontend = player.getKey();
            final int playerRef = getGameKeyForPlayer(playerFrontend);
            if (gameMechanics.isPlayerPresent(playerRef)) {
                final UserProfile playerProfile = player.getValue();
                gameStateMessage.addPlayer(
                        playerRef,
                        playerProfile.getId(),
                        gameMechanics.getPlayerScore(playerRef),
                        playerProfile.getLogin(),
                        gameMechanics.getPlayerHand(playerRef)
                );
            }
        }
        final FieldItem[][] rawField = gameMechanics.getRawField();
        for (int i = 0; i < rawField.length; i++) {
            for (int j = 0; j < rawField[i].length; j++) {
                if (rawField[i][j] != null) {
                    gameStateMessage.addFieldItem(Coordinate.fromRaw(i, j), rawField[i][j]);
                }
            }
        }
        return gameStateMessage;
    }

}
