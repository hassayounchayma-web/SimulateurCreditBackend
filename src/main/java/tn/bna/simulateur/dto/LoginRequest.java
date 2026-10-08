package tn.bna.simulateur.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;   // email ou CIN
    private String password;
}
