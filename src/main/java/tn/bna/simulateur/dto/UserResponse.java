package tn.bna.simulateur.dto;

import tn.bna.simulateur.entities.GestionUser.User;
import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String cin;
    private String role;

    public static UserResponse fromUser(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setNom(user.getNom());
        response.setPrenom(user.getPrenom());
        response.setEmail(user.getEmail());
        response.setTelephone(user.getTelephone());
        response.setCin(user.getCin());
        response.setRole(user.getRole());
        return response;
    }
}