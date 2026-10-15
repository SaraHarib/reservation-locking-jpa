package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import java.time.LocalDateTime;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;

public class ConcurrentReservationSimulator {

    public static void main(String[] args) {
        System.out.println("========== OPTIMISTIC LOCKING - CONFLIT ==========");
        EntityManagerFactory factory = Persistence.createEntityManagerFactory("reservationPU");
        try {
            ReservationService service = new ReservationServiceImpl(factory);
            LocalDateTime debut = LocalDateTime.of(2026, 10, 20, 9, 0);
            Reservation initiale = service.creerReservation(
                    new Reservation("Salle A", debut, debut.plusHours(1)));
            System.out.println("\n[INIT] Réservation créée : " + initiale);

            // Deux utilisateurs lisent avant la première écriture : versions identiques.
            Reservation tx1 = service.trouverParId(initiale.getId());
            Reservation tx2 = service.trouverParId(initiale.getId());
            System.out.println("\n[TX1] Version chargée : " + tx1.getVersion());
            System.out.println("[TX2] Version chargée : " + tx2.getVersion());
            if (tx1 == tx2 || tx1.getVersion() != 0 || tx2.getVersion() != 0) {
                throw new IllegalStateException("Deux instances indépendantes de version 0 sont attendues.");
            }

            tx1.setSalle("Salle B");
            tx1 = service.modifierReservation(tx1);
            System.out.println("\n[TX1] Modification réussie.");
            System.out.println("[TX1] Nouvelle version : " + tx1.getVersion());

            tx2.setSalle("Salle C");
            try {
                service.modifierReservation(tx2);
                throw new IllegalStateException("Le conflit optimiste attendu n'a pas été détecté.");
            } catch (OptimisticLockException exception) {
                System.out.println("\n[CONFLIT] OptimisticLockException détectée.");
                System.out.println("[TX2] La version utilisée était obsolète.");
            }

            Reservation finale = service.trouverParId(initiale.getId());
            System.out.println("\n[FINAL] " + finale);
            if (tx1.getVersion() != 1 || finale.getVersion() != 1
                    || !"Salle B".equals(finale.getSalle())) {
                throw new IllegalStateException("L'état final attendu est Salle B, version 1.");
            }
            System.out.println("\n[OK] Le verrouillage optimiste a empêché l'écrasement concurrent.");
        } finally {
            factory.close();
        }
    }
}
