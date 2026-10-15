package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import com.example.service.ReservationServiceImpl;
import java.time.LocalDateTime;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class ConcurrentReservationWithRetry {

    public static void main(String[] args) {
        System.out.println("========== OPTIMISTIC LOCKING - RETRY ==========");
        EntityManagerFactory factory = Persistence.createEntityManagerFactory("reservationPU");
        try {
            ReservationService service = new ReservationServiceImpl(factory);
            ReservationRetryHandler retryHandler = new ReservationRetryHandler(service);
            LocalDateTime debut = LocalDateTime.of(2026, 10, 20, 9, 0);
            Reservation initiale = service.creerReservation(
                    new Reservation("Salle A", debut, debut.plusHours(1)));
            System.out.println("\n[INIT] Réservation créée : " + initiale);

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
            System.out.println("[TX1] Version = " + tx1.getVersion());

            tx2.setSalle("Salle C");
            tx2 = retryHandler.modifierAvecRetry(tx2);
            System.out.println("\n[TX2] Modification réussie après retry.");
            System.out.println("[TX2] Version = " + tx2.getVersion());

            Reservation finale = service.trouverParId(initiale.getId());
            System.out.println("\n[FINAL] " + finale);
            if (tx1.getVersion() != 1 || tx2.getVersion() != 2 || finale.getVersion() != 2
                    || !"Salle C".equals(finale.getSalle())) {
                throw new IllegalStateException("L'état final attendu est Salle C, version 2.");
            }
            System.out.println("\n[OK] Le conflit a été détecté puis résolu par retry.");
        } finally {
            factory.close();
        }
    }
}
