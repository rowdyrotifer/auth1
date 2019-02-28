package org.auth1.auth1.dao;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.auth1.auth1.database.DatabaseLoader;
import org.auth1.auth1.model.DatabaseManager;
import org.auth1.auth1.model.entities.User;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserDaoImplTest {

    private static final String username = "user";
    private static final String password = "pass";
    private static final String incorrectPassword = "badpass";
    private static final byte[] totpSecret = new byte[128];
    private static final String email = "email@email.co";
    private static final boolean verified = false;
    private static final boolean locked = false;
    private static final ZonedDateTime creationTime = ZonedDateTime.now();

    private DatabaseLoader databaseLoader;
    private UserDao userDao;
    private User exampleUser;

    @BeforeAll
    void setUp() throws Exception {
        databaseLoader = new DatabaseLoader();
        databaseLoader.startDB();

        userDao = new UserDaoImpl(new DatabaseManager(DatabaseLoader.getDatabaseConfiguration()));
        exampleUser = new User(username, password, totpSecret, email, verified, locked, creationTime);
    }

    @AfterAll
    void tearDown() {
        databaseLoader.closeDB();
    }

    @BeforeEach
    void deleteUserTable() throws SQLException {
        final MysqlDataSource dataSource = DatabaseLoader.getMySqlDataSource();
        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("DELETE FROM User");
        } catch (SQLException e) {
            throw e;
        }
    }

    @Test
    void register() throws SQLException {
        userDao.register(exampleUser);
        final MysqlDataSource dataSource = DatabaseLoader.getMySqlDataSource();
        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM User;");
            rs.next();
            assertEquals(username, rs.getString("username"));
            assertEquals(password, rs.getString("password"));
            assertArrayEquals(totpSecret, rs.getBytes("totp_secret"));
            assertEquals(email, rs.getString("email"));
            assertEquals(verified, rs.getBoolean("verified"));
            assertEquals(locked, rs.getBoolean("locked"));
            assertTrue(creationTime.toLocalDate().isEqual(rs.getDate("creation_time").toLocalDate()));
        } catch (SQLException e) {
            throw e;
        }
    }

    @Test
    void login_correctCredentials() {
        userDao.register(exampleUser);
        assertTrue(userDao.login(username, password));
    }

    @Test
    void login_incorrectCredentials() {
        userDao.register(exampleUser);
        assertFalse(userDao.login(username, incorrectPassword));
    }

    @Test
    void login_nonexistentCredentials() {
        assertFalse(userDao.login(username, password));
    }

    @Test
    void getUserByUsername_exists() {
        userDao.register(exampleUser);
        final Optional<User> user = userDao.getUserByUsername(username);
        assertTrue(user.isPresent());
        assertEquals(exampleUser, user.get());
    }

    @Test
    void getUserByUsername_notExists() {
        assertTrue(userDao.getUserByUsername(username).isEmpty());
    }

    @Test
    void getUserByEmail_exists() {
        userDao.register(exampleUser);
        final Optional<User> user = userDao.getUserByEmail(email);
        assertTrue(user.isPresent());
        assertEquals(exampleUser, user.get());
    }

    @Test
    void getUserByEmail_notExists() {
        assertTrue(userDao.getUserByEmail(email).isEmpty());
    }
}