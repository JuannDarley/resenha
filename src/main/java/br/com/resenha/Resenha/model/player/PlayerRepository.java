package br.com.resenha.Resenha.model.player;

import ch.qos.logback.core.status.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUserId(Long userId);

    List<Player> findByStatus(PlayerStatus status);

    Optional<Player> findByUserIdAndStatus(Long userId, PlayerStatus status);
}
