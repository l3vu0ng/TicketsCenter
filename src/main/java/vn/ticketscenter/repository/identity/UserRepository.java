package vn.ticketscenter.repository.identity;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.model.identity.User;

import java.util.Optional;
import java.util.UUID;

public final class UserRepository {

    private final EntityManager entityManager;

    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<User> findByNormalizedEmail(String normalizedEmail) {
        return entityManager.createQuery(
                        "select user from User user where user.normalizedEmail = :email", User.class)
                .setParameter("email", normalizedEmail)
                .getResultStream()
                .findFirst();
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public void save(User user) {
        entityManager.persist(user);
    }
}
