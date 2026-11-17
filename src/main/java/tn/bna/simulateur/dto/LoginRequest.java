package tn.bna.simulateur.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {
    private String username;   // email ou CIN
    @ToString.Exclude
    private String password;
}
