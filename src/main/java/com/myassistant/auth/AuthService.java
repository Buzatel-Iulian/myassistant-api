package com.myassistant.auth;

import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.auth.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;

    // Two registrations of the same email at the same moment both pass this check; the unique index then refuses the
    // second one (DuplicateKeyException, also a 409).
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(userMapper.normalizeEmail(request.email()))) {
            throw new EmailAlreadyUsedException();
        }
        User user = userRepository.save(userMapper.toEntity(request, passwordEncoder.encode(request.password())));
        // The listeners (the starter kit) run now, in this thread; auth does not need to know what they create.
        eventPublisher.publishEvent(new UserRegistered(user.getId()));
        return userMapper.toResponse(user);
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
