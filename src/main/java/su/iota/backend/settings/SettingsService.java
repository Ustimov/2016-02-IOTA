package su.iota.backend.settings;

import co.paralleluniverse.fibers.SuspendExecution;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Contract;

@Contract
public interface SettingsService {

    int getServerPortSetting() throws SuspendExecution;

    @NotNull
    String getServerContextPathSetting() throws SuspendExecution;

    int getHttpSessionTimeoutSeconds() throws SuspendExecution;

    int getFrontendReceiveTimeoutSeconds() throws SuspendExecution;

    @NotNull
    String getDatabaseUserID() throws SuspendExecution;

    @NotNull
    String getDatabasePassword() throws SuspendExecution;

    @NotNull
    String getDatabaseName() throws SuspendExecution;

    int getPlayersInBucket() throws SuspendExecution;

    long getWsIdleTimeoutMillis() throws SuspendExecution;

}
