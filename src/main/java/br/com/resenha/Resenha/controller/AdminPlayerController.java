package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.player.DataDetailPlayer;
import br.com.resenha.Resenha.model.player.DataListPlayer;
import br.com.resenha.Resenha.model.player.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/players")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPlayerController {


    @Autowired
    private final PlayerRepository playerRepository;

    public AdminPlayerController(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }


    @GetMapping
    public ResponseEntity<Page<DataListPlayer>> list(@PageableDefault(size = 10, sort = {"name"}) Pageable paginacao) {
        var page = playerRepository.findAll(paginacao).map(DataListPlayer::new);
        return ResponseEntity.ok(page);
    }

    
}
