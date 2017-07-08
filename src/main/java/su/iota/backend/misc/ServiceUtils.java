package su.iota.backend.misc;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.ActorRegistry;
import co.paralleluniverse.actors.behaviors.AbstractServerHandler;
import co.paralleluniverse.actors.behaviors.Server;
import co.paralleluniverse.actors.behaviors.ServerActor;
import co.paralleluniverse.actors.behaviors.ServerHandler;
import co.paralleluniverse.fibers.SuspendExecution;
import org.glassfish.hk2.api.ServiceLocator;

public final class ServiceUtils {

    private static volatile Server<Class<?>, Object, Void> actor;

    private ServiceUtils() {
    }

    public static void setupServiceUtils(final ServiceLocator serviceLocator) throws SuspendExecution {
        if (actor != null) {
            throw new AssertionError();
        }
        final ServerHandler<Class<?>, Object, Void> serverHandler = new AbstractServerHandler<Class<?>, Object, Void>() {
            @Override
            public Object handleCall(ActorRef<?> from, Object id, Class<?> m) throws SuspendExecution {
                return serviceLocator.getService(m);
            }
        };
        //noinspection resource
        final ServerActor<Class<?>, Object, Void> actor = new ServerActor<>(serverHandler);
        ServiceUtils.actor = actor.spawnThread();
    }

    public static void teardownServiceUtils() throws SuspendExecution, InterruptedException {
        actor.shutdown();
        actor = null;
    }

    public static <T> T getService(Class<T> clazz) throws SuspendExecution, InterruptedException {
        if (actor == null) {
            throw new AssertionError();
        }
        //noinspection unchecked
        return (T) actor.call(clazz);
    }

}