package su.iota.backend.accounts;

import com.esotericsoftware.minlog.Log;
import org.jvnet.testing.hk2testng.HK2;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import su.iota.backend.accounts.exceptions.UserAlreadyExistsException;
import su.iota.backend.accounts.impl.AccountServiceMapImpl;
import su.iota.backend.main.ApplicationBootstrapper;
import su.iota.backend.models.UserProfile;

import javax.inject.Inject;
import javax.inject.Named;

import static org.testng.Assert.*;

//@HK2
@HK2(binders = ApplicationBootstrapper.DependencyBinder.class)
public class AccountServiceTest {

    private final String login = "Ustimov";
    private final String password = "Qwerty123!";
    private final String email = "art@ustimov.org";

    @Inject
//    @Named("AccountServiceMapImpl")
    private AccountService accountService;

//    @Test
//    public void testAccountServiceNamedInjection() throws Exception {
//        assertEquals(accountService.getClass(), AccountServiceMapImpl.class);
//    }

    @AfterMethod
    public void tearDown() throws Exception{
        deleteUserWithLogin(login);
    }

    public void deleteUserWithLogin(String userLogin) throws Exception {
        if (accountService.isUserExistent(userLogin)) {
            final Long id = accountService.getUserId(userLogin);
            if (id == null) {
                return;
            }
            accountService.deleteUser(id);
        }
    }

    @Test(expectedExceptions = UserAlreadyExistsException.class)
    public void testCreateUserWithUsedLogin() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        final String email2 = "admin1@mail.ru";
        final String password2 = "admin1";
        final UserProfile newUserProfile = new UserProfile(login, email2, password2);
        accountService.createUser(newUserProfile);
    }

    @Test(expectedExceptions = UserAlreadyExistsException.class)
    public void testRepeatedCreateUser() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        accountService.createUser(userProfile);
    }

    @Test
    public void testDeleteUser() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        accountService.deleteUser(userId);
        assertFalse(accountService.isUserExistent(userId));
    }

    @Test
    public void testGetUserId() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        final Long userIdByLogin = accountService.getUserId(login);
        if (userIdByLogin != null) {
            assertEquals((long) userIdByLogin, userId);
        }
    }

    @Test
    public void testGetUserProfileByLogin() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        final UserProfile userProfileByLogin = accountService.getUserProfile(login);
        assertNotNull(userProfileByLogin);
        userProfile.setId(userProfileByLogin.getId());
        assertEquals(userProfile, userProfileByLogin);
    }

    @Test
    public void testGetUserProfileById() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        final UserProfile userProfileById = accountService.getUserProfile(userId);
        assertNotNull(userProfileById);
        userProfile.setId(userProfileById.getId());
        assertEquals(userProfile, userProfileById);
    }

    @Test
    public void testIsUserPasswordCorrect() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        assertTrue(accountService.isUserPasswordCorrect(userId, password));
    }

    @Test
    public void testIsUserExistentByLogin() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        assertTrue(accountService.isUserExistent(login));
    }

    @Test
    public void testIsUserExistentById() throws Exception {
        final UserProfile userProfile = new UserProfile(login, email, password);
        final long userId = accountService.createUser(userProfile);
        assertTrue(accountService.isUserExistent(userId));
    }

}