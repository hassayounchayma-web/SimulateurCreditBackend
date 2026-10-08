package tn.bna.simulateur.config;

public class TestJwt {
    public static void main(String[] args) {
        JwtUtil jwtUtil = new JwtUtil();

        // Générer un token
        String token = jwtUtil.generateToken("test@email.com", "CLIENT");
        System.out.println("Token: " + token);

        // Valider le token
        boolean isValid = jwtUtil.validateToken(token);
        System.out.println("Token valide: " + isValid);

        // Extraire les informations
        String email = jwtUtil.extractUsername(token);
        String role = jwtUtil.extractRole(token);
        System.out.println("Email: " + email);
        System.out.println("Rôle: " + role);
    }
}