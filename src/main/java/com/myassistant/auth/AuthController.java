package com.myassistant.auth;

import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.HttpStatus.CREATED;

// Public endpoints: the empty @SecurityRequirements tells Swagger they need no token.
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {
    private final AuthService authService;

    @ApiResponse(responseCode = "201", description = "The account is created; log in next to get a token")
    @ApiResponse(responseCode = "400", description = "Invalid email, password shorter than 8 characters, or no display name",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "An account with this email already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/register")
    @ResponseStatus(CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @ApiResponse(responseCode = "200", description = "The token to send as Authorization: Bearer <token>")
    @ApiResponse(responseCode = "400", description = "Email or password missing",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Wrong email or password (the same answer for both)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
