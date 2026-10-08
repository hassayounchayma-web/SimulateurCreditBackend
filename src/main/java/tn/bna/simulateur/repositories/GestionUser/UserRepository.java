package tn.bna.simulateur.repositories.GestionUser;
import tn.bna.simulateur.entities.GestionUser.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByCin(String cin);
    boolean existsByEmail(String email);
    boolean existsByCin(String cin);
}
