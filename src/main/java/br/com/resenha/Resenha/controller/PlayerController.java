package br.com.resenha.Resenha.controller;


import br.com.resenha.Resenha.model.player.Player;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/players")
public class PlayerController {

    @PostMapping
    @Transactional
    public String cadastrar (@RequestBody Player dados){

        var player = new Player(dados);

        System.out.println(player);

        return "Cadastrado com sucesso";
    }
}
