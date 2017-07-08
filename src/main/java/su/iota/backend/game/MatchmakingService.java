package su.iota.backend.game;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.fibers.SuspendExecution;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Contract;
import su.iota.backend.models.UserProfile;

@Contract
public interface MatchmakingService {

    void makeMatch(@NotNull UserProfile player, @NotNull ActorRef<Object> frontend) throws SuspendExecution, InterruptedException;

    void dropPlayerFromMatchmaking(@NotNull ActorRef<Object> frontend) throws SuspendExecution;

}
