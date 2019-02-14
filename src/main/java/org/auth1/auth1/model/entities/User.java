package org.auth1.auth1.model.entities;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "User")
public class User {
    @Id
    @GeneratedValue
    @Column(name = "id")
    private int id;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "totp_secret")
    private byte[] totpSecret;

    @Column(name = "email")
    private String email;

    @Column(name = "verified")
    private boolean verified;

    @Column(name = "locked")
    private boolean locked;

    @Column(name = "creation_time")
    private ZonedDateTime creationTime;

}
