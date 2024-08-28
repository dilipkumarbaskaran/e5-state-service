package e5.stateservice.service;

import e5.stateservice.model.E5State;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.function.Consumer;

public final class E5StateService {

    public static final int BATCH_SIZE_SESSION_FLUSH = 20;

    public static <T extends E5State> E5StateIterable<T> find(Class<T> entityClass) {
        return new E5StateIterable<T>(entityClass, E5StateServiceInitializer.sessionFactory);
    }

    @Transactional
    public static <T extends E5State> T insertOne(T entity) {
        try (var session = E5StateServiceInitializer.sessionFactory.openSession()) {
            session.beginTransaction();
            session.save(entity);
            session.getTransaction().commit();
        }
        return entity;
    }

    @Transactional
    public static <T extends E5State> List<T> insertMany(List<T> entities) {
        executeInsideTransaction(session -> {
            for (int i = 0; i < entities.size(); i++) {
                session.save(entities.get(i));
                if (i % BATCH_SIZE_SESSION_FLUSH == 0) { // Batch size can be adjusted
                    session.flush();
                    session.clear();
                }
            }
        });
        return entities;
    }

    //@Transactional
    public static <T extends E5State> T updateOne(T entity) {
        executeInsideTransaction(session -> {
            session.update(entity);
        });
        return entity;
    }

    public static <T extends E5State> List<T> updateMany(List<T> entities) {
        executeInsideTransaction(session -> {
            for (int i = 0; i < entities.size(); i++) {
                session.update(entities.get(i));
                if (i % BATCH_SIZE_SESSION_FLUSH == 0) { // Batch size can be adjusted
                    session.flush();
                    session.clear();
                }
            }
        });
        return entities;
    }

    @Transactional
    public static <T extends E5State> boolean deleteOne(Class<T> entityClass, long id) {
        executeInsideTransaction(session -> {
            T entity = (T) session.get(entityClass, id);
            if (entity != null) {
                session.delete(entity);
            }
        });
        return true;
    }





    private static void executeInsideTransaction(Consumer<Session> action) {
        Transaction transaction = null;
        try (Session session = E5StateServiceInitializer.sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            action.accept(session);
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null) transaction.rollback();
            throw e;
        }
    }
}
