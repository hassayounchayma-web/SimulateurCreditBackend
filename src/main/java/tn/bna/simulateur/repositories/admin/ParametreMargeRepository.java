package tn.bna.simulateur.repositories.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.bna.simulateur.entities.admin.ParametreMarge;

import java.util.List;
import java.util.Optional;

public interface ParametreMargeRepository
        extends JpaRepository<ParametreMarge, Long> {

    Optional<ParametreMarge> findByScenarioAndTypeCredit(
            String scenario,
            String typeCredit
    );

    List<ParametreMarge> findByScenarioOrderByTypeCreditAsc(
            String scenario
    );

    List<ParametreMarge> findAllByOrderByScenarioAscTypeCreditAsc();
}