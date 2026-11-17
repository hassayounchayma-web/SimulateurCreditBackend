package tn.bna.simulateur.dto.dossier;

public record ChecklistRequest(
        String simulationJson,
        String typeVendeur,
        Boolean revenusSupplementairesDeclares
) {
}
