package com.myassistant.auth;

import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.auth.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Two registrations of the same email at the same moment both pass this check; the unique index then refuses the
    // second one (DuplicateKeyException, also a 409).
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(userMapper.normalizeEmail(request.email()))) {
            throw new EmailAlreadyUsedException();
        }
        User user = userMapper.toEntity(request, passwordEncoder.encode(request.password()));
        return userMapper.toResponse(userRepository.save(user));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(userMapper.normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return jwtService.issueToken(user.getId());
    }

    public UserResponse findById(String userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
