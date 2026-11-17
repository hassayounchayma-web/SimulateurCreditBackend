package tn.bna.simulateur.entities.admin;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "parametre_marge",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_marge_scenario_produit",
                columnNames = {"scenario", "type_credit"}
        )
)
@Getter
@Setter
public class ParametreMarge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String scenario;

    @Column(name = "type_credit", nullable = false, length = 255)
    private String typeCredit;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal marge;

    @Column(name = "max_mois", nullable = false)
    private Integer maxMois;

    @Column(name = "date_modification", nullable = false)
    private LocalDateTime dateModification;

    @PrePersist
    @PreUpdate
    public void onSave() {
        dateModification = LocalDateTime.now();
    }
}