package br.com.resenha.Resenha.model.player;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUserId(Long userId);

    Page<Player> findAllByStatus(PlayerStatus status, Pageable pageable);

    Optional<Player> findByUserIdAndStatus(Long userId, PlayerStatus status);
}
