package e5.stateservice.service;

import e5.stateservice.model.E5FieldEnum;
import e5.stateservice.model.E5State;

import javax.transaction.Transactional;

public final class E5StateService {

    public static <T extends E5State, F extends Enum<F> & E5FieldEnum> E5StateIterable<T, F> find(Class<T> entityClass, Class<F> FieldENum) {
        return new E5StateIterable<T, F>(entityClass, E5StateInitializer.sessionFactory);
    }

    @Transactional
    public static <T extends E5State> void insertOne(T entity) {
        try (var session = E5StateInitializer.sessionFactory.openSession()) {
            session.beginTransaction();
            session.save(entity);
            session.getTransaction().commit();
        }
    }

    @Transactional
    public static <T extends E5State> void deleteOne(Class<T> entityClass, int id) {
        try (var session = E5StateInitializer.sessionFactory.openSession()) {
            session.beginTransaction();
            T entity = (T) session.get(entityClass, id);
            if (entity != null) {
                session.delete(entity);
                session.getTransaction().commit();
            }
        }
    }

    @Transactional
    public static <T extends E5State> void updateOne(T entity) {
        try (var session = E5StateInitializer.sessionFactory.openSession()) {
            session.beginTransaction();
            session.update(entity);
            session.getTransaction().commit();
        }
    }
}
