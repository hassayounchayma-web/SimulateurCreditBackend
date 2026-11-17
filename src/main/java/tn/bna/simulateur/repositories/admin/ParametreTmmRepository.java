package tn.bna.simulateur.repositories.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.bna.simulateur.entities.admin.ParametreTmm;

public interface ParametreTmmRepository
        extends JpaRepository<ParametreTmm, Long> {
}