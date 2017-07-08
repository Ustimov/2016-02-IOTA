package su.iota.backend.game.impl;

import co.paralleluniverse.actors.behaviors.ProxyServerActor;
import co.paralleluniverse.fibers.SuspendExecution;
import org.eclipse.jetty.util.ArrayQueue;
import org.glassfish.hk2.api.PerLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.game.GameMechanics;
import su.iota.backend.models.game.*;

import java.util.*;

@Service
@PerLookup
public final class GameMechanicsImpl extends ProxyServerActor implements GameMechanics {

    private Deque<Integer> players = new ArrayDeque<>();

    @NotNull
    private UUID currentGameStateUuid = UUID.randomUUID();

    public GameMechanicsImpl() {
        super(true);
    }

    private Field field = new Field();
    private Queue<FieldItem> cardDeck = new LinkedList<>();
    private Map<UUID, FieldItem> cardsDrawn = new HashMap<>();
    private Set<UUID> cardsPlayed = new HashSet<>();
    private Map<Integer, Set<UUID>> playerHands = new HashMap<>();
    private Map<Integer, Integer> playerScores = new HashMap<>();
    private boolean initialized = false;
    private boolean concluded = false;
    private boolean passAllowed = true;

    @Override
    public void initialize() throws SuspendExecution {
        if (!initialized) {
            initCardDeck();
            for (int player : players) {
                giveCards(player);
            }
            updateGameStateUuid();
            final FieldItem card = drawCard();
            if (card == null) {
                throw new AssertionError();
            }
            field.placeCard(Field.CENTER_COORDINATE, card);
            initialized = true;
        }
    }

    @NotNull
    @Override
    public UUID getCurrentGameStateUuid() throws SuspendExecution {
        return currentGameStateUuid;
    }

    private void updateGameStateUuid() throws SuspendExecution {
        currentGameStateUuid = UUID.randomUUID();
    }

    @Override
    public boolean tryPlayCard(int player, @NotNull Coordinate coordinate, @NotNull UUID uuid) throws SuspendExecution {
        final FieldItem card = getDrawnCardByUuid(uuid);
        return card != null && tryPlayCardInternal(player, coordinate, card, false);
    }

    @Override
    public boolean tryEphemeralPlayCard(int player, @NotNull Coordinate coordinate, @NotNull UUID uuid) throws SuspendExecution {
        final FieldItem card = getDrawnCardByUuid(uuid);
        return card != null && tryPlayCardInternal(player, coordinate, card, true);
    }

    @SuppressWarnings("OverlyComplexMethod")
    private boolean tryPlayCardInternal(int player, @NotNull Coordinate coordinate, @NotNull FieldItem card, boolean isEphemeral) throws SuspendExecution {
        if (concluded || !coordinate.isInRange() || card.isEphemeral()) {
            return false;
        }
        final UUID uuid = card.getUuid();
        final Set<UUID> playerHand = playerHands.get(player);
        //noinspection OverlyComplexBooleanExpression
        if (playerHand == null
                || !playerHand.contains(uuid)
                || !cardsDrawn.containsKey(uuid)
                || cardsPlayed.contains(uuid)
                || !field.isPlacementCorrect(coordinate, card)) {
            return false;
        }
        if (!isEphemeral) {
            passAllowed = false;
            playCardInternal(player, coordinate, card);
            updateGameStateUuid();
        }
        if (playerHand.isEmpty()) {
            if (cardDeck.isEmpty()) {
                concluded = true;
            } else {
                endTurn(player);
            }
        }
        return true;
    }

    private void playCardInternal(int player, @NotNull Coordinate coordinate, @NotNull FieldItem card) throws SuspendExecution {
        final Set<UUID> playerHand = playerHands.get(player);
        final UUID uuid = card.getUuid();
        playerHand.remove(uuid);
        cardsPlayed.add(uuid);
        final int currentScore = playerScores.get(player);
        final int addScore = field.placeCard(coordinate, card);
        playerScores.put(player, currentScore + addScore);
    }

    @Override
    public boolean tryPassCard(int player, @NotNull UUID uuid) throws SuspendExecution {
        return tryPassCardInternal(player, uuid, false);
    }

    @Override
    public boolean tryEphemeralPassCard(int player, @NotNull UUID uuid) throws SuspendExecution {
        return tryPassCardInternal(player, uuid, true);
    }

    private boolean tryPassCardInternal(int player, @NotNull UUID uuid, boolean isEphemeral) throws SuspendExecution {
        //noinspection OverlyComplexBooleanExpression
        if (concluded
                || !passAllowed
                || cardsPlayed.contains(uuid)
                || !playerHands.containsKey(player)) {
            return false;
        }
        final Set<UUID> playerHand = playerHands.get(player);
        if (playerHand == null || !playerHand.contains(uuid)) {
            return false;
        }
        if (!isEphemeral) {
            final FieldItem card = cardsDrawn.get(uuid);
            if (card == null) {
                return false;
            }
            card.setPassed(true);
        }
        endTurnIfAllPassed(player);
        return true;
    }

    private void endTurnIfAllPassed(int player) throws SuspendExecution {
        final Set<UUID> playerHand = playerHands.get(player);
        if (playerHand == null) {
            return;
        }
        boolean allPassed = true;
        for (final UUID cardUuid : playerHand) {
            final FieldItem card = cardsDrawn.get(cardUuid);
            if (card != null && !card.isPassed()) {
                allPassed = false;
                break;
            }
        }
        if (allPassed) {
            endTurn(player);
        }
    }

    @Nullable
    private FieldItem drawCard() throws SuspendExecution {
        final FieldItem drawnCard = cardDeck.peek();
        if (drawnCard == null) {
            return null;
        }
        cardDeck.remove();
        final UUID uuid = drawnCard.getUuid();
        if (cardsDrawn.containsKey(uuid)) {
            throw new AssertionError();
        }
        cardsDrawn.put(uuid, drawnCard);
        return drawnCard;
    }

    @Override
    public boolean endTurn(int player) throws SuspendExecution {
        final Integer headPlayer = players.peek();
        final boolean isOk = !concluded && headPlayer != null && headPlayer == player;
        if (isOk) {
            endTurnInternal();
        }
        return isOk;
    }

    @Nullable
    @Override
    public FieldItem getDrawnCardByUuid(@NotNull UUID uuid) throws SuspendExecution {
        return cardsDrawn.get(uuid);
    }

    private void endTurnInternal() throws SuspendExecution {
        if (players.isEmpty()) {
            throw new AssertionError();
        }
        final int headPlayer = players.removeFirst();
        final Set<UUID> playerHand = playerHands.get(headPlayer);
        if (playerHand != null) {
            final Set<UUID> passedCards = new HashSet<>();
            for (UUID uuid : playerHand) {
                final FieldItem card = cardsDrawn.get(uuid);
                if (card != null && card.isPassed()) {
                    passedCards.add(uuid);
                }
            }
            for (UUID uuid : passedCards) {
                final FieldItem card = cardsDrawn.get(uuid);
                card.setPassed(false);
                playerHand.remove(uuid);
                cardDeck.add(cardsDrawn.remove(uuid));
            }
        }
        giveCards(headPlayer);
        players.addLast(headPlayer);
        passAllowed = true;
        if (checkGameConcluded()) {
            concluded = true;
        }
    }

    private boolean checkGameConcluded() throws SuspendExecution {
        if (players.size() < 2) {
            return true;
        }
        if (!cardDeck.isEmpty()) {
            return false;
        }
        for (final Set<UUID> uuids : playerHands.values()) {
            if (!uuids.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getPlayerScore(int player) throws SuspendExecution {
        return playerScores.get(player);
    }

    @Nullable
    @Override
    public Integer getCurrentPlayer() throws SuspendExecution {
        return players.peek();
    }

    @Override
    public boolean isConcluded() throws SuspendExecution {
        return concluded;
    }

    @Override
    public void setConcluded(boolean concluded) throws SuspendExecution {
        this.concluded = concluded;
    }

    @Override
    public boolean addPlayer(int player) throws SuspendExecution {
        if (concluded) {
            return false;
        }
        final boolean exists = players.contains(player) || playerHands.containsKey(player);
        if (!exists) {
            playerHands.put(player, new HashSet<>());
            playerScores.put(player, 0);
            players.push(player);
        }
        return !exists;
    }

    @Override
    public void dropPlayer(int player) throws SuspendExecution {
        if (players.isEmpty()) {
            return;
        }
        players.remove(player);
        final Set<UUID> playerHand = playerHands.remove(player);
        if (playerHand != null) {
            for (UUID uuid : playerHand) {
                final FieldItem card = cardsDrawn.remove(uuid);
                if (card != null) {
                    cardDeck.add(card);
                }
            }
        }
        if (players.size() < 2) {
            concluded = true;
        }
    }

    @Override
    public boolean isPlayerPresent(int player) throws SuspendExecution {
        return players.contains(player);
    }

    private void giveCards(int headPlayer) throws SuspendExecution {
        final Set<UUID> hand = playerHands.get(headPlayer);
        if (hand == null) {
            throw new AssertionError();
        }
        while (hand.size() < 4) {
            final FieldItem card = drawCard();
            if (card == null) {
                break;
            }
            hand.add(card.getUuid());
        }
    }

    private void initCardDeck() throws SuspendExecution {
        final List<FieldItem> cards = new ArrayList<>();
        for (FieldItem.Color color : FieldItem.Color.values()) {
            for (FieldItem.Shape shape : FieldItem.Shape.values()) {
                for (FieldItem.Number number : FieldItem.Number.values()) {
                    final Card card = new Card(color, shape, number);
                    cards.add(card);
                }
            }
        }
//        for (int i = 0; i < 2; i++) {
//            final Wildcard wildcard = new Wildcard();
//            cards.add(wildcard);
//        }
        Collections.shuffle(cards);
        cardDeck.addAll(cards);
    }

    @NotNull
    @Override
    public FieldItem[][] getRawField() throws SuspendExecution {
        return field.getRawField();
    }

    @Nullable
    @Override
    public Collection<FieldItem> getPlayerHand(int playerRef) throws SuspendExecution {
        final Set<UUID> uuidCards = playerHands.get(playerRef);
        if (uuidCards == null) {
            return null;
        }
        final Set<FieldItem> cards = new HashSet<>();
        for (UUID uuid : uuidCards) {
            cards.add(getDrawnCardByUuid(uuid));
        }
        return cards;
    }

}
