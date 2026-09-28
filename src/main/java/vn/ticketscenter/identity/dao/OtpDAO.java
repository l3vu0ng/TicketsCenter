package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.IdentityEnums;

import java.time.Instant;
import java.util.Optional;

public final class OtpDAO {

    private final EntityManager entityManager;

    public OtpDAO(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void save(Otp otp) {
        entityManager.persist(otp);
    }

    public Optional<Otp> findLatest(String emailNormalized, IdentityEnums.OtpPurpose purpose) {
        return entityManager.createQuery(
                        "select o from Otp o where o.emailNormalized = :email and o.purpose = :purpose order by o.createdAt desc", Otp.class)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public Optional<Otp> findActiveWithLock(String emailNormalized, IdentityEnums.OtpPurpose purpose) {
        return entityManager.createQuery(
                        "select o from Otp o where o.emailNormalized = :email and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null order by o.createdAt desc", Otp.class)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
    }

    public int invalidateAllActive(String emailNormalized, IdentityEnums.OtpPurpose purpose, Instant invalidatedAt) {
        return entityManager.createQuery(
                        "update Otp o set o.invalidatedAt = :invalidatedAt where o.emailNormalized = :email and o.purpose = :purpose and o.consumedAt is null and o.invalidatedAt is null")
                .setParameter("invalidatedAt", invalidatedAt)
                .setParameter("email", emailNormalized)
                .setParameter("purpose", purpose)
                .executeUpdate();
    }
}
