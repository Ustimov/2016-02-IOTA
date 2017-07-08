package su.iota.backend.messages.internal;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.RequestMessage;
import org.jetbrains.annotations.NotNull;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;
import su.iota.backend.models.UserProfile;

import java.util.Map;

public class GameSessionInitMessage extends RequestMessage<Boolean> implements IncomingMessage {

    @NotNull
    private final Map<ActorRef<Object>, UserProfile> players;

    public GameSessionInitMessage(@NotNull Map<ActorRef<Object>, UserProfile> players) {
        this.players = players;
    }

    @NotNull
    public Map<ActorRef<Object>, UserProfile> getPlayers() {
        return players;
    }

    public static class Result implements OutgoingMessage {

        private boolean ok;

        public Result(boolean ok) {
            this.ok = ok;
        }

        public boolean isOk() {
            return ok;
        }

        public void setOk(boolean ok) {
            this.ok = ok;
        }
    }

}
