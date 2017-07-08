package su.iota.backend.settings.impl;

import co.paralleluniverse.fibers.SuspendExecution;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.glassfish.hk2.api.Immediate;
import org.jetbrains.annotations.NotNull;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.settings.SettingsService;

@Service
@Immediate
public class SettingsServiceImpl implements SettingsService {

    private final Config config;

    public SettingsServiceImpl() {
        config = ConfigFactory.load();
        config.checkValid(ConfigFactory.defaultReference());
    }

    @Override
    public int getServerPortSetting() throws SuspendExecution {
        return config.getInt("port");
    }

    @NotNull
    @Override
    public String getServerContextPathSetting() throws SuspendExecution {
        return config.getString("contextPath");
    }

    @Override
    public int getHttpSessionTimeoutSeconds() throws SuspendExecution {
        return config.getInt("httpSessionTimeoutSeconds");
    }

    @Override
    public int getFrontendReceiveTimeoutSeconds() throws SuspendExecution {
        return config.getInt("frontendReceiveTimeoutSeconds");
    }

    @NotNull
    @Override
    public String getDatabaseUserID() throws SuspendExecution {
        return config.getString("db.user");
    }

    @NotNull
    @Override
    public String getDatabasePassword() throws SuspendExecution {
        return config.getString("db.password");
    }

    @NotNull
    @Override
    public String getDatabaseName() throws SuspendExecution {
        return config.getString("db.name");
    }

    @Override
    public int getPlayersInBucket() throws SuspendExecution {
        return config.getInt("playersInBucket");
    }

    @Override
    public long getWsIdleTimeoutMillis() throws SuspendExecution {
        return config.getLong("wsIdleTimeoutMillis");
    }

}
