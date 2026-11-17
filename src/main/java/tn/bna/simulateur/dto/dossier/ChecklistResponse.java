package tn.bna.simulateur.dto.dossier;

import java.util.List;

public record ChecklistResponse(
        String produit,
        String produitCode,
        String scenario,
        String typeVendeur,
        boolean typeVendeurRequis,
        boolean revenusSupplementairesDeclares,
        boolean complete,
        List<Item> documents,
        List<AlternativeGroup> groupesAlternatifs,
        List<String> documentsManquants,
        List<Rule> reglesEnAttente
) {
    public record Item(
            String code,
            String libelle,
            String description,
            String statut,
            boolean obligatoire,
            boolean actif,
            boolean depose,
            boolean genereAutomatiquement,
            String condition,
            String regle
    ) { }

    public record AlternativeGroup(
            String code,
            String libelle,
            boolean obligatoire,
            boolean satisfait,
            List<String> documentsAcceptes,
            List<String> documentsDeposes
    ) { }

    public record Rule(String code, String statut, String description) { }
}
