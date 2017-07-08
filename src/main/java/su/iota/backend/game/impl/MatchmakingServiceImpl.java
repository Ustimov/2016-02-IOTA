package su.iota.backend.game.impl;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.actors.behaviors.Server;
import co.paralleluniverse.fibers.SuspendExecution;
import com.esotericsoftware.minlog.Log;
import org.eclipse.jetty.util.ArrayQueue;
import org.glassfish.hk2.api.ServiceLocator;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.game.GameSessionActor;
import su.iota.backend.game.MatchmakingService;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;
import su.iota.backend.messages.internal.GameSessionInitMessage;
import su.iota.backend.models.UserProfile;
import su.iota.backend.settings.SettingsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Singleton
public class MatchmakingServiceImpl extends ProxyServerActor implements MatchmakingService {

    @Inject
    private ServiceLocator serviceLocator;

    @Inject
    private SettingsService settingsService;

    private final Queue<PlayerBucket> buckets = new ArrayQueue<>();

    public MatchmakingServiceImpl() {
        super(false);
    }

    @Override
    public void makeMatch(@NotNull UserProfile player, @NotNull ActorRef<Object> frontend) throws SuspendExecution, InterruptedException {
        final int playersInBucket = settingsService.getPlayersInBucket();
        if (!buckets.isEmpty()) {
            if (!buckets.stream().anyMatch(bucket -> bucket.tryPut(frontend, player))) {
                Log.info("MM: tryPut() failed for all possible buckets for player " + player.getLogin());
            }
        } else {
            final PlayerBucket newBucket = new PlayerBucket(playersInBucket);
            if (!newBucket.tryPut(frontend, player)) {
                throw new AssertionError();
            }
            buckets.add(newBucket);
        }

        final List<PlayerBucket> fullBuckets = buckets.stream().filter(PlayerBucket::isFull).collect(Collectors.toList());
        for (PlayerBucket fullBucket : fullBuckets) {
            final Map<ActorRef<Object>, UserProfile> players = fullBucket.getPlayers();
            final Server<IncomingMessage, OutgoingMessage, Object> gameSession = createGameSession(players);
            fullBucket.setGameSession(gameSession);
            for (ActorRef<Object> playerFrontend : players.keySet()) {
                playerFrontend.send(gameSession);
            }
        }
        buckets.removeIf(b -> b.getGameSession() != null);
    }

    @Override
    public void dropPlayerFromMatchmaking(@NotNull ActorRef<Object> frontend) {
        final List<PlayerBucket> emptyBuckets = new ArrayList<>();
        for (PlayerBucket bucket : buckets) {
            bucket.dropPlayer(frontend);
            if (bucket.isEmpty()) {
                emptyBuckets.add(bucket);
            }
        }
        if (!emptyBuckets.isEmpty()) {
            buckets.removeIf(emptyBuckets::contains);
            Log.info("Dropping player from matchmaking along with " + emptyBuckets.size() + " buckets, frontend: " + frontend.toString());
        }
    }

    private Server<IncomingMessage, OutgoingMessage, Object> createGameSession(@NotNull Map<ActorRef<Object>, UserProfile> players) throws SuspendExecution, InterruptedException {
        final Collection<String> playerNames = new HashSet<>();
        for (UserProfile userProfile : players.values()) {
            playerNames.add(userProfile.getLogin());
        }
        Log.info(String.format("Making match for players! ( %s )", String.join("; ", playerNames)));
        final Server<IncomingMessage, OutgoingMessage, Object> gameSession = serviceLocator.getService(GameSessionActor.class).spawn();
        final GameSessionInitMessage.Result result = (GameSessionInitMessage.Result) gameSession.call(new GameSessionInitMessage(players));
        if (!result.isOk()) {
            throw new AssertionError();
        }
        return gameSession;
    }

    private static class PlayerBucket {

        private final int capacity;
        private final Map<ActorRef<Object>, UserProfile> players;
        private Server<IncomingMessage, OutgoingMessage, Object> gameSession;

        PlayerBucket(int capacity) {
            this.capacity = capacity;
            players = new HashMap<>(capacity);
        }

        public boolean isFull() {
            return capacity <= players.size();
        }

        public boolean isEmpty() {
            return players.isEmpty();
        }

        public boolean tryPut(ActorRef<Object> frontend, UserProfile player) {
            return !isFull() && players.putIfAbsent(frontend, player) == null;
        }

        public void dropPlayer(@NotNull ActorRef<Object> player) {
            players.remove(player);
        }

        public Map<ActorRef<Object>, UserProfile> getPlayers() {
            return players;
        }

        public Server<IncomingMessage, OutgoingMessage, Object> getGameSession() {
            return gameSession;
        }

        public void setGameSession(Server<IncomingMessage, OutgoingMessage, Object> gameSession) {
            this.gameSession = gameSession;
        }

    }

}
