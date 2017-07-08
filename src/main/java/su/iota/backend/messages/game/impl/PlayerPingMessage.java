package su.iota.backend.messages.game.impl;

import com.google.gson.annotations.Expose;
import su.iota.backend.messages.game.AbstractPlayerActionMessage;

public class PlayerPingMessage extends AbstractPlayerActionMessage {

    // -- todo: remove after debugging ------------
    @Expose
    private boolean debugConclude = false;

    public boolean isDebugConclude() {
        return debugConclude;
    }
    // --------------------------------------------

    public static class ResultMessage extends AbstractPlayerActionMessage.AbstractResultMessage {

        {
            ok = true;
        }

    }

}
