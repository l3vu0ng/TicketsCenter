package vn.ticketscenter.order.repository;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.order.model.Coupon;

import java.util.Optional;
import java.util.UUID;

public class CouponRepository {

    public Optional<Coupon> findByCode(EntityManager em, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return em.createQuery("""
                        select c from Coupon c
                        where upper(trim(c.code)) = upper(trim(:code))
                        """, Coupon.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst();
    }

    public Optional<Coupon> findById(EntityManager em, UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(em.find(Coupon.class, id));
    }
}
