// CreditSimulatorApiiApplication.java
package tn.bna.simulateur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "tn.bna.simulateur")  // ← Ajouter ceci
public class CreditSimulatorApiiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditSimulatorApiiApplication.class, args);
        System.out.println("🚀 Application démarrée sur http://localhost:8081");
        System.out.println("📝 Endpoints disponibles:");
        System.out.println("   POST /api/auth/register");
        System.out.println("   POST /api/auth/login");
    }
}