package com.myassistant.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// A user account in the users collection. The constraints are checked before every save (MongoConfig).
// No toString: it would print the password hash into a log.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Document(collection = "users")
public class User {
    @Id
    private String id;

    // Stored lower-cased (UserMapper), so the unique index also refuses the same address in another letter case.
    @Indexed(unique = true)
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String passwordHash;

    @NotBlank
    private String displayName;

    @NotNull
    private Instant createdAt;
}
