package tn.bna.simulateur.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    @ToString.Exclude
    private String token;
    private String type;
    private UserResponse user;
}