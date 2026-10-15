package com.example;

import com.example.model.Reservation;
import com.example.service.ReservationService;
import java.time.LocalDateTime;
import javax.persistence.EntityNotFoundException;
import javax.persistence.OptimisticLockException;

public class ReservationRetryHandler {

    // Au maximum trois tentatives de modification, premier essai inclus.
    private static final int MAX_RETRIES = 3;

    private final ReservationService reservationService;

    public ReservationRetryHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    public Reservation modifierAvecRetry(Reservation reservation) {
        Long id = reservation.getId();
        // Mémoriser l'intention utilisateur avant tout rechargement.
        String salleSouhaitee = reservation.getSalle();
        LocalDateTime dateDebutSouhaitee = reservation.getDateDebut();
        LocalDateTime dateFinSouhaitee = reservation.getDateFin();

        for (int tentative = 1; tentative <= MAX_RETRIES; tentative++) {
            try {
                return reservationService.modifierReservation(reservation);
            } catch (OptimisticLockException exception) {
                System.out.println("[RETRY] Conflit détecté - tentative "
                        + tentative + "/" + MAX_RETRIES);
                if (tentative == MAX_RETRIES) {
                    throw exception;
                }

                // Le service ouvre un nouvel EntityManager après chaque échec.
                reservation = reservationService.trouverParId(id);
                if (reservation == null) {
                    throw new EntityNotFoundException("Réservation introuvable : " + id);
                }
                System.out.println("[RETRY] Version rechargée : " + reservation.getVersion());
                reservation.setSalle(salleSouhaitee);
                reservation.setDateDebut(dateDebutSouhaitee);
                reservation.setDateFin(dateFinSouhaitee);
            }
        }
        throw new IllegalStateException("Aucune tentative de modification effectuée.");
    }
}
