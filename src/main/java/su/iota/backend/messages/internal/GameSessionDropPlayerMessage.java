package su.iota.backend.messages.internal;

import co.paralleluniverse.actors.ActorRef;
import org.jetbrains.annotations.NotNull;
import su.iota.backend.messages.IncomingMessage;

public class GameSessionDropPlayerMessage implements IncomingMessage {

    @NotNull
    private final ActorRef<Object> player;

    public GameSessionDropPlayerMessage(@NotNull ActorRef<Object> player) {
        this.player = player;
    }

    @NotNull
    public ActorRef<Object> getPlayer() {
        return player;
    }

}
