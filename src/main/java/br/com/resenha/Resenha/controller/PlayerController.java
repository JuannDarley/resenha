package br.com.resenha.Resenha.controller;


import br.com.resenha.Resenha.model.player.DataDetailPlayer;
import br.com.resenha.Resenha.model.player.DataRegisterPlayer;
import br.com.resenha.Resenha.model.player.Player;
import br.com.resenha.Resenha.model.player.PlayerRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/players")
public class PlayerController {


    @Autowired
    private PlayerRepository  playerRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register (@RequestBody @Valid DataRegisterPlayer data, UriComponentsBuilder uriBuilder) {


        var player = new Player(data);
        playerRepository.save(player);
        var uri = uriBuilder.path("/players/{id}").buildAndExpand(player.getId()).toUri();
        return ResponseEntity.created(uri).body(new DataDetailPlayer(player));
    }
}
