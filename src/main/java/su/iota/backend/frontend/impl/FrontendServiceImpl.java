package su.iota.backend.frontend.impl;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.Server;
import co.paralleluniverse.fibers.SuspendExecution;
import org.glassfish.hk2.api.PerLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.accounts.AccountService;
import su.iota.backend.accounts.exceptions.UserAlreadyExistsException;
import su.iota.backend.accounts.exceptions.UserNotFoundException;
import su.iota.backend.frontend.FrontendService;
import su.iota.backend.game.MatchmakingService;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;
import su.iota.backend.messages.game.AbstractPlayerActionMessage;
import su.iota.backend.messages.game.impl.IllegalPlayerActionResultMessage;
import su.iota.backend.messages.game.impl.PlayerPingMessage;
import su.iota.backend.messages.internal.GameSessionDropPlayerMessage;
import su.iota.backend.models.UserProfile;

import javax.inject.Inject;

@Service
@PerLookup
public class FrontendServiceImpl implements FrontendService {

    @Inject
    MatchmakingService matchmakingService;

    @Inject
    AccountService accountService;

    private UserProfile signedInUser;
    private Server<IncomingMessage, OutgoingMessage, Object> gameSession;

    @Override
    public boolean signUp(@Nullable UserProfile userProfile) throws SuspendExecution {
        if (userProfile == null) {
            return false;
        }
        final String login = userProfile.getLogin();
        final String password = userProfile.getPassword();
        if (login.isEmpty() || password.isEmpty()) {
            return false;
        }
        try {
            final long userId = accountService.createUser(userProfile);
            userProfile.setId(userId);
            if (!signIn(userProfile)) {
                throw new AssertionError();
            }
            return true;
        } catch (UserAlreadyExistsException ignored) {
        }
        return false;
    }

    @Override
    public boolean signIn(@Nullable UserProfile userProfile) throws SuspendExecution {
        if (userProfile == null) {
            return false;
        }
        final String login = userProfile.getLogin();
        final String password = userProfile.getPassword();
        if (login.isEmpty() || password.isEmpty()) {
            return false;
        }
        final Long userId = accountService.getUserId(login);
        if (userId == null) {
            return false;
        }
        try {
            if (accountService.isUserPasswordCorrect(userId, password)) {
                signedInUser = accountService.getUserProfile(userId);
                return true;
            }
        } catch (UserNotFoundException ignored) {
        }
        return false;
    }

    @Override
    public void signOut() throws SuspendExecution {
        resetGameSession();
        signedInUser = null;
    }

    @Nullable
    @Override
    public UserProfile getSignedInUser() throws SuspendExecution {
        return signedInUser;
    }

    @Nullable
    @Override
    public UserProfile getUserById(long userId) throws SuspendExecution {
        if (!accountService.isUserExistent(userId)) {
            return null;
        }
        return accountService.getUserProfile(userId);
    }

    @NotNull
    @Override
    public AbstractPlayerActionMessage.AbstractResultMessage performPlayerAction(@NotNull AbstractPlayerActionMessage playerActionMessage) throws SuspendExecution, InterruptedException {
        if (signedInUser == null) {
            return new IllegalPlayerActionResultMessage();
        }
        if (gameSession == null) {
            if (playerActionMessage instanceof PlayerPingMessage && playerActionMessage.isGoodbyeMessage()) {
                //noinspection unchecked
                final ActorRef<Object> frontend = (ActorRef<Object>) playerActionMessage.getFrom();
                if (frontend == null) {
                    return new IllegalPlayerActionResultMessage();
                }
                softDropPlayer(frontend);
                return new PlayerPingMessage.ResultMessage();
            } else {
                return new IllegalPlayerActionResultMessage();
            }
        }
        final OutgoingMessage result = gameSession.call(playerActionMessage);
        if (!(result instanceof AbstractPlayerActionMessage.AbstractResultMessage)) {
            throw new AssertionError();
        }
        return (AbstractPlayerActionMessage.AbstractResultMessage) result;
    }

    @Override
    public boolean askGameStateUpdate(@NotNull ActorRef<Object> frontend) throws SuspendExecution, InterruptedException {
        if (signedInUser == null) {
            return false;
        }
        if (gameSession != null) {
            gameSession.cast(frontend);
        } else {
            matchmakingService.makeMatch(signedInUser, frontend);
        }
        return true;
    }

    @Override
    public void setGameSession(@NotNull ActorRef<Object> frontend, Server<IncomingMessage, OutgoingMessage, Object> gameSession) throws SuspendExecution, InterruptedException {
        this.gameSession = gameSession;
        if (!askGameStateUpdate(frontend)) {
            throw new AssertionError();
        }
    }

    @Override
    public void resetGameSession() throws SuspendExecution {
        gameSession = null;
    }

    @Override
    public void dropPlayer(@NotNull ActorRef<Object> frontend) throws SuspendExecution, InterruptedException {
        softDropPlayer(frontend);
        if (gameSession != null) {
            gameSession.cast(new GameSessionDropPlayerMessage(frontend));
        }
        resetGameSession();
    }

    @Override
    public void softDropPlayer(@NotNull ActorRef<Object> frontend) throws SuspendExecution {
        matchmakingService.dropPlayerFromMatchmaking(frontend);
    }

}
