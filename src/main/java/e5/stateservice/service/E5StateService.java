package e5.stateservice.service;

import e5.stateservice.model.E5State;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.util.List;
import java.util.function.Consumer;

public final class E5StateService {

    public static final int BATCH_SIZE_SESSION_FLUSH = 20;

    public static <T extends E5State> E5StateIterable<T> find(SessionFactory sessionFactory, Class<T> entityClass) {
        return new E5StateIterable<T>(entityClass, sessionFactory);
    }

    public static <T extends E5State> T insertOne(SessionFactory sessionFactory, T entity) {
        executeInsideTransaction(session -> {
            session.save(entity);
        }, sessionFactory);
        return entity;
    }

    public static <T extends E5State> List<T> insertMany(SessionFactory sessionFactory, List<T> entities) {
        executeInsideTransaction(session -> {
            for (int i = 0; i < entities.size(); i++) {
                session.save(entities.get(i));
                if (i % BATCH_SIZE_SESSION_FLUSH == 0) { // Batch size can be adjusted
                    session.flush();
                    session.clear();
                }
            }
        }, sessionFactory);
        return entities;
    }

    public static <T extends E5State> T updateOne(SessionFactory sessionFactory, T entity) {
        executeInsideTransaction(session -> {
            session.update(entity);
        }, sessionFactory);
        return entity;
    }

    public static <T extends E5State> List<T> updateMany(SessionFactory sessionFactory, List<T> entities) {
        executeInsideTransaction(session -> {
            for (int i = 0; i < entities.size(); i++) {
                session.update(entities.get(i));
                if (i % BATCH_SIZE_SESSION_FLUSH == 0) { // Batch size can be adjusted
                    session.flush();
                    session.clear();
                }
            }
        }, sessionFactory);
        return entities;
    }

    @Transactional
    public static <T extends E5State> boolean deleteOne(SessionFactory sessionFactory, Class<T> entityClass, long id) {
        executeInsideTransaction(session -> {
            T entity = (T) session.get(entityClass, id);
            if (entity != null) {
                session.delete(entity);
            }
        }, sessionFactory);
        return true;
    }





    private static void executeInsideTransaction(Consumer<Session> action, SessionFactory sessionFactory) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            action.accept(session);
            transaction.commit();
        } catch (RuntimeException e) {
            if (transaction != null) {
                if (transaction != null && transaction.isActive()) {
                    try {
                        transaction.rollback();
                    } catch(Exception ex) {
                        //don't do anything
                    }
                }
            }
            throw e;
        }
    }
}
