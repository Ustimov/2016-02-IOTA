package su.iota.backend.main;

import co.paralleluniverse.comsat.webactors.servlet.WebActorInitializer;
import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import co.paralleluniverse.strands.SuspendableRunnable;
import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.api.Immediate;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.jvnet.hk2.annotations.Service;
import su.iota.backend.accounts.AccountService;
import su.iota.backend.accounts.impl.AccountServiceJdbiImpl;
import su.iota.backend.misc.ServiceUtils;
import su.iota.backend.settings.SettingsService;

import java.net.InetSocketAddress;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.sql.DataSource;

@Service
@Immediate
public final class ApplicationBootstrapper implements SuspendableRunnable {

    @Inject
    ServiceLocator serviceLocator;

    @Inject
    SettingsService settingsService;

    public Server setupServer() throws SuspendExecution {
        final Server server = new Server(new InetSocketAddress("127.0.0.1", settingsService.getServerPortSetting()));
        final ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        WebActorInitializer.setUserClassLoader(ClassLoader.getSystemClassLoader());
        server.setHandler(contextHandler);
        contextHandler.setContextPath(settingsService.getServerContextPathSetting());
        contextHandler.addEventListener(new WebActorInitializer());
        contextHandler.getSessionHandler().getSessionManager().setMaxInactiveInterval(
                settingsService.getHttpSessionTimeoutSeconds()
        );
        try {
            final ServerContainer serverContainer
                    = WebSocketServerContainerInitializer.configureContext(contextHandler);
            serverContainer.getPolicy().setIdleTimeout(
                    settingsService.getWsIdleTimeoutMillis()
            );
        } catch (ServletException e) {
            throw new AssertionError(e);
        }
        return server;
    }

    @Override
    public void run() throws SuspendExecution, InterruptedException {
        final Server server = setupServer();
        //noinspection AnonymousInnerClassMayBeStatic
        server.addLifeCycleListener(new AbstractLifeCycle.AbstractLifeCycleListener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                System.out.println(ASCII_LOGO);
            }
        });
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        server.join();
    }

    public static ServiceLocator setupServiceLocator(Binder binder) throws SuspendExecution {
        final ServiceLocator serviceLocator = ServiceLocatorUtilities.createAndPopulateServiceLocator();
        ServiceLocatorUtilities.bind(serviceLocator, binder);
        ServiceLocatorUtilities.enableImmediateScope(serviceLocator);
        return serviceLocator;
    }

    public static void main(String[] args) throws SuspendExecution, InterruptedException {
        final ServiceLocator serviceLocator = setupServiceLocator(new DependencyBinder());
        ServiceUtils.setupServiceUtils(serviceLocator);
        serviceLocator.getService(ApplicationBootstrapper.class).run();
        ServiceUtils.teardownServiceUtils();
    }

    public static class DependencyBinder extends AbstractBinder {

        @Override
        protected void configure() {
            bindFactory(DataSourceFactory.class).to(DataSource.class).in(Immediate.class);
            bind(AccountServiceJdbiImpl.class).to(AccountService.class).in(Immediate.class);
        }

    }

    private static class DataSourceFactory implements Factory<DataSource> {

        @Inject
        private SettingsService settingsService;

        @Suspendable
        @Override
        public DataSource provide() {
            final MysqlDataSource dataSource = new MysqlDataSource();
            try {
                dataSource.setDatabaseName(settingsService.getDatabaseName());
                dataSource.setUser(settingsService.getDatabaseUserID());
                dataSource.setPassword(settingsService.getDatabasePassword());
            } catch (SuspendExecution ex) {
                throw new AssertionError(ex);
            }
            return dataSource;
        }

        @Override
        public void dispose(DataSource instance) {
        }

    }

    private static final String ASCII_LOGO = "" +
            "\n.__        __                             ___    \n" +
            "|__| _____/  |______         ________ __  \\  \\   \n" +
            "|  |/  _ \\   __\\__  \\       /  ___/  |  \\  \\  \\  \n" +
            "|  (  <_> )  |  / __ \\_     \\___ \\|  |  /   )  ) \n" +
            "|__|\\____/|__| (____  / /\\ /____  >____/   /  /  \n" +
            "                    \\/  \\/      \\/        /__/   \n";

}
