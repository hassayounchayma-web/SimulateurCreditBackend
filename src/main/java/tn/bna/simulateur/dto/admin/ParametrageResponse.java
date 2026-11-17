
package tn.bna.simulateur.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import tn.bna.simulateur.entities.admin.ParametreMarge;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParametrageResponse {

    private BigDecimal tmm;

    private List<ParametreMarge> marges;
}
