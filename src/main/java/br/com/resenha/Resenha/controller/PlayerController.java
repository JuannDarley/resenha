package br.com.resenha.Resenha.controller;


import br.com.resenha.Resenha.model.player.*;
import br.com.resenha.Resenha.model.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import br.com.resenha.Resenha.infra.security.JWTUserData;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.model.user.UserRepository;
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
    public ResponseEntity register (@RequestBody @Valid DataRegisterPlayer data, UriComponentsBuilder uriBuilder) {

        var authentication = SecurityContextHolder.getContext().getAuthentication();

        JWTUserData userData = (JWTUserData) authentication.getPrincipal();

        User user = userRepository.findById(userData.userId())
                .orElseThrow();


        var player = new Player(data, user);
        playerRepository.save(player);
        var uri = uriBuilder.path("/players/{id}").buildAndExpand(player.getId()).toUri();
        return ResponseEntity.created(uri).body(new DataDetailPlayer(player));
    }

    @GetMapping
    public ResponseEntity<Page<DataListPlayer>> list(@PageableDefault(size = 10, sort = {"name"}) Pageable paginacao) {
        var page = playerRepository.findAll(paginacao).map(DataListPlayer::new);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity detail(@PathVariable Long id) {
        var player = playerRepository.getReferenceById(id);
        return ResponseEntity.ok(new DataDetailPlayer(player));
    }

    @PutMapping
    @Transactional
    public ResponseEntity update(@RequestBody @Valid DataUpadatePlayer data) {
        var player = playerRepository.getReferenceById(data.id());
        player.updateInformation(data);

        return ResponseEntity.ok(new DataDetailPlayer(player));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity delete(@PathVariable Long id) {
        playerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
