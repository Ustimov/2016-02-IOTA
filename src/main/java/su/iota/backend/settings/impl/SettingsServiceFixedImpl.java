package su.iota.backend.settings.impl;

import co.paralleluniverse.fibers.SuspendExecution;
import org.glassfish.hk2.api.Rank;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.settings.SettingsService;

import javax.inject.Named;
import javax.inject.Singleton;

@Rank(-100)
@Service
@Singleton
public class SettingsServiceFixedImpl implements SettingsService {

    @Override
    public int getServerPortSetting() throws SuspendExecution {
        //noinspection MagicNumber
        return 8089;
    }

    @NotNull
    @Override
    public String getServerContextPathSetting() throws SuspendExecution {
        return "/test/api";
    }

    @Override
    public int getHttpSessionTimeoutSeconds() throws SuspendExecution {
        //noinspection MagicNumber
        return 3600;
    }

    @Override
    public int getFrontendReceiveTimeoutSeconds() throws SuspendExecution {
        //noinspection MagicNumber
        return 12;
    }

    @NotNull
    @Override
    public String getDatabaseUserID() throws SuspendExecution {
        return "unused";
    }

    @NotNull
    @Override
    public String getDatabasePassword() throws SuspendExecution {
        return "unused";
    }

    @NotNull
    @Override
    public String getDatabaseName() throws SuspendExecution {
        return "unused";
    }

    @Override
    public int getPlayersInBucket() throws SuspendExecution {
        return 2;
    }

    @Override
    public long getWsIdleTimeoutMillis() throws SuspendExecution {
        //noinspection MagicNumber
        return 1800000;
    }

}
