package vn.ticketscenter.repository.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import vn.ticketscenter.model.identity.Otp;
import vn.ticketscenter.model.identity.OtpPurpose;

import java.time.Instant;
import java.util.Optional;

public final class OtpRepository {

    private final EntityManager entityManager;

    public OtpRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void save(Otp otp) {
        entityManager.persist(otp);
    }

    public Optional<Otp> findLatest(String emailNormalized, OtpPurpose purpose) {
        return entityManager.createQuery(
                        "select o from Otp o where o.emailNormalized = :email and o.purpose = :purpose order by o.createdAt desc", Otp.class)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<Otp> findActiveWithLock(String emailNormalized, OtpPurpose purpose) {
        return entityManager.createQuery(
                        "select o from Otp o where o.emailNormalized = :email and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null order by o.createdAt desc", Otp.class)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public int invalidateAllActive(String emailNormalized, OtpPurpose purpose, Instant invalidatedAt) {
        return entityManager.createQuery(
                        "update Otp o set o.invalidatedAt = :invalidatedAt where o.emailNormalized = :email and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null")
                .setParameter("invalidatedAt", invalidatedAt)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .executeUpdate();
    }
}
