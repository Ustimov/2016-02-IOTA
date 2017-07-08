package su.iota.backend.accounts;

import co.paralleluniverse.fibers.SuspendExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jvnet.hk2.annotations.Contract;
import su.iota.backend.accounts.exceptions.UserAlreadyExistsException;
import su.iota.backend.accounts.exceptions.UserNotFoundException;
import su.iota.backend.models.UserProfile;

@Contract
public interface AccountService {

    long createUser(@NotNull UserProfile userProfile) throws SuspendExecution, UserAlreadyExistsException;

    void editUser(long userId, @NotNull UserProfile newUserProfile) throws SuspendExecution, UserNotFoundException, UserAlreadyExistsException;

    void deleteUser(long userId) throws SuspendExecution, UserNotFoundException;

    @Nullable Long getUserId(@NotNull String userLogin) throws SuspendExecution;

    @Nullable UserProfile getUserProfile(@NotNull String userLogin) throws SuspendExecution;

    @Nullable UserProfile getUserProfile(long userId) throws SuspendExecution;

    boolean isUserPasswordCorrect(long userId, @NotNull String password) throws SuspendExecution, UserNotFoundException;

    boolean isUserExistent(long userId) throws SuspendExecution;

    boolean isUserExistent(@NotNull String userLogin) throws SuspendExecution;

}
