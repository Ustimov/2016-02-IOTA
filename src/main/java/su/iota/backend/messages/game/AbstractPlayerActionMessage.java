package su.iota.backend.messages.game;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.behaviors.FromMessage;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import su.iota.backend.messages.IncomingMessage;
import su.iota.backend.messages.OutgoingMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public abstract class AbstractPlayerActionMessage implements FromMessage, IncomingMessage {

    @SuppressWarnings("InstanceVariableNamingConvention")
    @Nullable
    private String __type;

    @Expose
    protected boolean ephemeral = false;

    @Expose
    protected boolean endSequence = false;

    @Expose
    protected boolean goodbye = false;

    @Nullable
    protected ActorRef<?> from;

    @Nullable
    @Expose
    protected String commentary;

    public boolean isEphemeral() {
        return ephemeral;
    }

    public boolean isEndSequenceTrigger() {
        return endSequence;
    }

    public boolean isGoodbyeMessage() {
        return goodbye;
    }

    @Nullable
    @Override
    public ActorRef<?> getFrom() {
        return from;
    }

    public void setFrom(@Nullable ActorRef<?> from) {
        this.from = from;
    }

    public boolean isCommentaryAttached() {
        return commentary != null && !commentary.isEmpty();
    }

    public abstract static class AbstractResultMessage implements OutgoingMessage {

        @SerializedName("__ok")
        @Expose
        protected boolean ok = false;

        @Expose
        protected boolean broadcast = false;

        public boolean isOk() {
            return ok;
        }

        public boolean isBroadcastTrigger() {
            return broadcast;
        }

        public void setBroadcastTrigger(boolean broadcast) {
            this.broadcast = broadcast;
        }
        
    }

}
