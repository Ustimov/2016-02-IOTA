package su.iota.backend.models;

import com.google.gson.annotations.Expose;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UserProfile {

    @NotNull
    @Expose
    private Long id = -1L;

    @NotNull
    @Expose
    private String login = "";

    @NotNull
    @Expose
    private String email = "";

    @NotNull
    private String password = "";

    public UserProfile() {
    }

    public UserProfile(long id, @NotNull String login, @NotNull String email) {
        this.id = id;
        this.login = login;
        this.email = email;
    }

    public UserProfile(@NotNull String login, @NotNull String email, @NotNull String password) {
        this.login = login;
        this.email = email;
        this.password = password;
    }

    public UserProfile(long id, @NotNull String login, @NotNull String email, @NotNull String password) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.password = password;
    }

    @NotNull
    public Long getId() {
        return id;
    }

    public void setId(@NotNull Long id) {
        this.id = id;
    }

    @NotNull
    public String getLogin() {
        return login;
    }

    public void setLogin(@NotNull String login) {
        this.login = login;
    }

    @NotNull
    public String getEmail() {
        return email;
    }

    public void setEmail(@NotNull String email) {
        this.email = email;
    }

    @NotNull
    public String getPassword() {
        return password;
    }

    public void setPassword(@NotNull String password) {
        this.password = password;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        final UserProfile userProfile = (UserProfile) obj;

        if (!id.equals(userProfile.id)) return false;
        //noinspection SimplifiableIfStatement
        if (!login.equals(userProfile.login)) return false;
        return email.equals(userProfile.email);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + login.hashCode();
        result = 31 * result + email.hashCode();
        return result;
    }

    @NotNull
    @Override
    public String toString() {
        return "UserProfile{" +
                "id=" + id +
                ", login='" + login + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

}
