package org.auth1.auth1.model.entities;

import org.springframework.util.Base64Utils;

import javax.persistence.*;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

@Entity
@Table(name = "LoginToken")
public class LoginToken extends UserToken {

    @Column(name = "issued_ip")
    private String issuedIp;

    @Column(name = "issued_user_agent")
    private String issuedUserAgent;

    public LoginToken(String value, int userId, ZonedDateTime issueTime, ZonedDateTime expirationTime, String issuedIp, String issuedUserAgent) {
        super(value, userId, issueTime, expirationTime);
        this.issuedIp = issuedIp;
        this.issuedUserAgent = issuedUserAgent;
    }

    public static LoginToken withDuration(int userId, long time, TimeUnit unit) {
        ZonedDateTime issueTime = ZonedDateTime.now();
        ZonedDateTime expirationTime = issueTime.plusSeconds(unit.toSeconds(time));
        SecureRandom random = new SecureRandom();
        final byte[] bytes = new byte[128];
        random.nextBytes(bytes);
        final String value = Base64Utils.encodeToString(bytes);
        return new LoginToken(value, userId, issueTime, expirationTime, null, null);
    }

    public String getIssuedIp() {
        return issuedIp;
    }

    public String getIssuedUserAgent() {
        return issuedUserAgent;
    }

    @Override
    public String toString() {
        return "LoginToken{" +
                "issuedIp='" + issuedIp + '\'' +
                ", issuedUserAgent='" + issuedUserAgent + '\'' +
                '}';
    }
}
