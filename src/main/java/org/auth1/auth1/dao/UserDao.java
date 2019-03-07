package org.auth1.auth1.dao;

import org.auth1.auth1.model.entities.User;

import java.util.Optional;

public interface UserDao {
    boolean login(final String username, final String password);

    void saveUser(final User user);

    void setPasswordResetToken(final String username, final String passwordResetToken);

    void lockUser(final String username);

    void unlockUser(final String username);

    void resetPassword(final String username, final String password);


    Optional<User> getUserById(final int userId);

    Optional<User> getUserByUsername(final String username);

    Optional<User> getUserByEmail(final String email);

    Optional<User> getUserByUsernameOrEmail(final String usernameOrEmail);


}
