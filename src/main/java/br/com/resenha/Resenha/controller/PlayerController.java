package br.com.resenha.Resenha.controller;


import br.com.resenha.Resenha.model.player.*;
import br.com.resenha.Resenha.model.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import br.com.resenha.Resenha.model.user.User;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/players")
public class PlayerController {


    @Autowired
    private PlayerRepository  playerRepository;
    @Autowired
    private UserRepository userRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register(@RequestBody @Valid DataRegisterPlayer data,
                                   UriComponentsBuilder uriBuilder) {

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        if (playerRepository.findByUserId(user.getId()).isPresent()) {
            throw new RuntimeException("User já possui um player");
        }

        Player player = new Player(data, user);
        playerRepository.save(player);
        var uri = uriBuilder.path("/players/{id}")
                .buildAndExpand(player.getId())
                .toUri();

        return ResponseEntity.created(uri).body(new DataDetailPlayer(player));
    }


    @GetMapping("/me")
    public ResponseEntity myProfile() {

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();
        Player player = playerRepository.findByUserIdAndStatus(user.getId(), PlayerStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Player inativo ou não existe"));
        return ResponseEntity.ok(new DataDetailPlayer(player));

    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity update(@RequestBody @Valid DataUpadatePlayer data) {



        User user = (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        Player player = playerRepository.findByUserIdAndStatus(user.getId(), PlayerStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Player inativo"));

        return ResponseEntity.ok(new DataDetailPlayer(player));
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity delete() {

        User user = (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        Player player = playerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Player not found"));

        player.deactivate();
        playerRepository.save(player);

        return ResponseEntity.noContent().build();
    }
}
