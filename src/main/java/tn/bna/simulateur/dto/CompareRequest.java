package tn.bna.simulateur.dto;

import lombok.Data;
import java.util.List;

@Data
public class CompareRequest {
    /** IDs des simulations à comparer (2 ou 3 max) */
    private List<Long> ids;
}