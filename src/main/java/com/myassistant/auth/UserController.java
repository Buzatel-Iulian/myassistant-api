package com.myassistant.auth;

import com.myassistant.auth.dto.UserResponse;
import com.myassistant.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final AuthService authService;

    @ApiResponse(responseCode = "200", description = "The logged-in user")
    @ApiResponse(responseCode = "401", description = "No token, or the token is invalid or expired",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/me")
    UserResponse me() {
        return authService.findById(CurrentUser.id());
    }
}
