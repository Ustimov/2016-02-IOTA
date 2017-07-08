package su.iota.backend.messages.game.impl;

import su.iota.backend.messages.game.AbstractPlayerActionMessage;

public class IllegalPlayerActionResultMessage extends AbstractPlayerActionMessage.AbstractResultMessage {

    public IllegalPlayerActionResultMessage() {
        ok = false;
    }

}
