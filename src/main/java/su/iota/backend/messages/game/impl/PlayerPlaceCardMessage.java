package su.iota.backend.messages.game.impl;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.iota.backend.messages.game.AbstractPlayerActionMessage;
import su.iota.backend.models.game.Coordinate;

import java.util.UUID;

public class PlayerPlaceCardMessage extends AbstractPlayerActionMessage {

    @Expose
    private Integer offX;

    @Expose
    private Integer offY;

    @Expose
    private String uuid;

    @Nullable
    public Coordinate getCoordinate() {
        if (offX == null || offY == null) {
            return null;
        }
        return new Coordinate(offX, offY);
    }

    @Nullable
    public UUID getUuid() {
        if (uuid == null) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static class ResultMessage extends AbstractPlayerActionMessage.AbstractResultMessage {

        public ResultMessage(boolean ok) {
            this.ok = ok;
            if (ok) {
                broadcast = true;
            }
        }

    }

}
