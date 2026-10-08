package tn.bna.simulateur.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimulateurInfo {
    private String nom;
    private String route;
    private String description;
    private String[] champsRequis;
}