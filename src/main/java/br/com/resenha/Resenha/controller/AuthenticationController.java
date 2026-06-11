package br.com.resenha.Resenha.controller;


import br.com.resenha.Resenha.infra.security.DataToken;
import br.com.resenha.Resenha.infra.security.TokenConfig;
import br.com.resenha.Resenha.model.player.Player;
import br.com.resenha.Resenha.model.player.PlayerRepository;
import br.com.resenha.Resenha.model.user.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenConfig tokenConfig;

    public AuthenticationController (UserRepository userRepository, PlayerRepository playerRepository,PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenConfig tokenConfig) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenConfig = tokenConfig;
    }

    @PostMapping("/login")
    public ResponseEntity<DataToken> login(@Valid @RequestBody DataAuthentication data) {

        UsernamePasswordAuthenticationToken userAndPass = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        Authentication authentication = authenticationManager.authenticate(userAndPass);

        User user = (User) authentication.getPrincipal();
        String token = tokenConfig.generateToken(user);

        return ResponseEntity.ok(new DataToken(token));

    }

    @PostMapping("/register")
    public ResponseEntity<DataRegisterResponse> register(@Valid @RequestBody DataRegisterRequest data) {

        if (userRepository.findUserByEmail(data.email()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }
       
        User newUser = new User();

        newUser.setPassword(passwordEncoder.encode(data.password()));
        newUser.setEmail(data.email());
        newUser.setRole(data.role());

        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(new DataRegisterResponse(newUser.getEmail(), newUser.getRole()));
    }
}
