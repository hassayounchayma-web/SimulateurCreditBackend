package tn.bna.simulateur.entities.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "parametre_tmm")
@Getter
@Setter
public class ParametreTmm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal valeur;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @PrePersist
    @PreUpdate
    public void onSave() {
        dateModification = LocalDateTime.now();
    }
}