package tn.bna.simulateur.dto;
import lombok.Data;
import lombok.ToString;

@Data
public class RegisterRequest {
    private String nom;
    private String prenom;
    private String email;
    @ToString.Exclude
    private String password;
    private String telephone;
    private String cin;
}