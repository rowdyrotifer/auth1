package org.auth1.auth1.core.authentication;

import static org.auth1.auth1.core.authentication.AuthenticationStepResult.of;
import static org.auth1.auth1.core.authentication.AuthenticationStepResult.stepPassed;
import static org.auth1.auth1.model.Auth1Configuration.RequiredUserFields.EMAIL_ONLY;
import static org.auth1.auth1.model.Auth1Configuration.RequiredUserFields.USERNAME_AND_EMAIL;
import static org.auth1.auth1.model.Auth1Configuration.RequiredUserFields.USERNAME_ONLY;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.auth1.auth1.dao.TokenDao;
import org.auth1.auth1.dao.UserDao;
import org.auth1.auth1.model.Auth1Configuration;
import org.auth1.auth1.model.entities.PasswordResetToken;
import org.auth1.auth1.model.entities.User;
import org.auth1.auth1.model.entities.UserAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationManager {
    private final Auth1Configuration config;
    private final UserDao userDao;
    private final TokenDao tokenDao;
    private final List<AuthenticationStep> steps;

    public AuthenticationManager(Auth1Configuration config, UserDao userDao, TokenDao tokenDao) {
        this.config = config;
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.steps = StreamSupport.stream(STEPS.spliterator(), false)
                .map(f -> f.apply(this))
                .collect(Collectors.toList());
    }

    /**
     * Wrapper for inner functions that expect an unlocked User to perform their functions.
     */
    private <T> T performWithExistingAndUnlockedAccount(UserIdentifier userId, Function<User, T> function,
                                                        T accountDoesNotExistResult, T accountIsLockedResult) {
        return userId
                .getUser(userDao)
                .map(user -> {
                    if (user.isLocked()) {
                        return accountIsLockedResult;
                    } else {
                        return function.apply(user);
                    }
                })
                .orElse(accountDoesNotExistResult);
    }

  public GeneratePasswordResetTokenResult generatePasswordResetToken(UserIdentifier userId) {
        return performWithExistingAndUnlockedAccount(
                userId,
                this::generatePasswordResetToken,
            GeneratePasswordResetTokenResult.ACCOUNT_DOES_NOT_EXIST,
            GeneratePasswordResetTokenResult.ACCOUNT_LOCKED
        );
    }

  private GeneratePasswordResetTokenResult generatePasswordResetToken(User user) {
        final PasswordResetToken token = PasswordResetToken.withDuration(user.getId(), 1, TimeUnit.HOURS);
        tokenDao.savePasswordResetToken(token);
    return GeneratePasswordResetTokenResult
        .forSuccess(new ExpiringToken(token.getValue(), token.getExpirationTime()));
    }

    public ResetPasswordResult resetPassword(String passwordResetToken, String newPassword) {
        return tokenDao.getPasswordResetToken(passwordResetToken)
                .map(PasswordResetToken::getUserId)
                .flatMap(userDao::getUserById)
                .map(user -> {
                    if (this.passwordConformsToRules(newPassword)) {
                        user.setPassword(newPassword);
                        userDao.saveUser(user);
                        return ResetPasswordResult.SUCCESS;
                    } else {
                        return ResetPasswordResult.INVALID_PASSWORD;
                    }
                }).orElse(ResetPasswordResult.INVALID_TOKEN);
    }


    private boolean passwordConformsToRules(String newPassword) {
        return true; // TODO: password strength policy
    }

    public CheckAuthenticationTokenResult checkAuthenticationToken(String token) {
        return tokenDao
                .getAuthToken(token)
                .map(UserAuthenticationToken::getUserId)
                .map(CheckAuthenticationTokenResult::forSuccess)
                .orElseGet(CheckAuthenticationTokenResult::forInvalid);
    }

    public RegistrationResult register(@Nullable final String username, @Nullable final String email, final String rawPassword) {
        final var required = this.config.getRequiredUserFields();

        if ((required == USERNAME_ONLY || required == USERNAME_AND_EMAIL) && username == null) {
            return RegistrationResult.USERNAME_REQUIRED;
        } else if ((required == EMAIL_ONLY || required == USERNAME_AND_EMAIL) && email == null) {
            return RegistrationResult.EMAIL_REQUIRED;
        }

        final var password = this.config.getHashFunction().hash(rawPassword);
        final var newUser = new User(username, password, null, email, false, false, ZonedDateTime.now());
        userDao.saveUser(newUser);
        return this.config.isEmailVerificationRequired()
                ? RegistrationResult.SUCCESS_CONFIRM_EMAIL : RegistrationResult.SUCCESS;
    }

    public AuthenticationResult authenticate(final UserIdentifier userId, final String rawPassword, final @Nullable String totpCode) {
        return performWithExistingAndUnlockedAccount(userId,
                user -> this.authenticate(user, rawPassword, totpCode),
                AuthenticationResult.USER_DOES_NOT_EXIST,
                AuthenticationResult.ACCOUNT_LOCKED);
    }

    private AuthenticationResult authenticate(final User user, final String rawPassword, final @Nullable String totpCode) {
        return steps.stream()
                .map(step -> step.doStep(user, rawPassword, totpCode))
                .filter(AuthenticationStepResult::failed)
                .findFirst()
                .flatMap(AuthenticationStepResult::getResult)
                .orElseGet(() -> {
                    final UserAuthenticationToken token = UserAuthenticationToken.withDuration(user.getId(), 23, TimeUnit.HOURS);
                    tokenDao.saveLoginToken(token);
                    return AuthenticationResult.forSuccess(new ExpiringToken(token.getValue(), token.getExpirationTime()));
                });
    }

    private static Iterable<Function<AuthenticationManager, AuthenticationStep>> STEPS = Arrays.asList(
            mgr -> mgr::checkLocked,
            mgr -> mgr::checkRate,
            mgr -> mgr::checkVerified,
            mgr -> mgr::checkPassword,
            mgr -> mgr::checkTOTP);

    private AuthenticationStepResult checkLocked(User user, final String rawPassword, final @Nullable String totpCode) {
        return !user.isLocked() ? stepPassed() : of(AuthenticationResult.ACCOUNT_LOCKED);
    }

    private AuthenticationStepResult checkRate(User user, final String rawPassword, final @Nullable String totpCode) {
        return stepPassed(); // no rate limiting right now.
    }

    private AuthenticationStepResult checkVerified(User user, final String rawPassword, final @Nullable String totpCode) {
        final var notRequiredOrVerified = !this.config.isEmailVerificationRequired() || user.isVerified();
        return notRequiredOrVerified ? stepPassed() : of(AuthenticationResult.NOT_VERIFIED);
    }

    private AuthenticationStepResult checkPassword(User user, final String rawPassword, final @Nullable String totpCode) {
        final var match = this.config.getCheckFunction().check(rawPassword, user.getPassword());
        return match ? stepPassed() : of(AuthenticationResult.BAD_PASSWORD);
    }

    private AuthenticationStepResult checkTOTP(User user, final String rawPassword, final @Nullable String totpCode) {
        return stepPassed(); // no TOTP right now.
    }

}
