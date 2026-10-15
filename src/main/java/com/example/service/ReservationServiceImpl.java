package com.example.service;

import com.example.model.Reservation;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.RollbackException;

public class ReservationServiceImpl implements ReservationService {

    private final EntityManagerFactory entityManagerFactory;

    public ReservationServiceImpl(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public Reservation creerReservation(Reservation reservation) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            entityManager.persist(reservation);
            transaction.commit();
            return reservation;
        } catch (RuntimeException exception) {
            rollbackSiActive(transaction, exception);
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    @Override
    public Reservation modifierReservation(Reservation reservation) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        EntityTransaction transaction = entityManager.getTransaction();
        try {
            transaction.begin();
            // merge conserve le contrôle de la version de l'entité détachée.
            Reservation reservationGeree = entityManager.merge(reservation);
            transaction.commit();
            // merge renvoie une autre instance ; sa version est à jour après commit.
            return reservationGeree;
        } catch (OptimisticLockException exception) {
            rollbackSiActive(transaction, exception);
            throw exception;
        } catch (RollbackException exception) {
            rollbackSiActive(transaction, exception);
            // Un conflit détecté au commit peut être enveloppé par JPA.
            Throwable cause = exception.getCause();
            while (cause != null) {
                if (cause instanceof OptimisticLockException) {
                    throw (OptimisticLockException) cause;
                }
                cause = cause.getCause();
            }
            throw exception;
        } catch (RuntimeException exception) {
            rollbackSiActive(transaction, exception);
            throw exception;
        } finally {
            entityManager.close();
        }
    }

    @Override
    public Reservation trouverParId(Long id) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return entityManager.find(Reservation.class, id);
        } finally {
            // Chaque lecture renvoie une instance détachée indépendante.
            entityManager.close();
        }
    }

    @Override
    public List<Reservation> listerReservations() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return entityManager.createQuery(
                    "SELECT r FROM Reservation r ORDER BY r.id", Reservation.class)
                    .getResultList();
        } finally {
            entityManager.close();
        }
    }

    private void rollbackSiActive(EntityTransaction transaction, RuntimeException exception) {
        try {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } catch (RuntimeException rollbackException) {
            // Préserver l'erreur initiale, notamment le conflit optimiste.
            exception.addSuppressed(rollbackException);
        }
    }
}
