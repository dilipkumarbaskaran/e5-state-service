package e5.stateservice.service;

import e5.stateservice.model.E5FieldEnum;
import e5.stateservice.model.E5State;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.reflections.Reflections;

import javax.transaction.Transactional;
import java.util.Set;

public final class E5StateService {
    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        Configuration configuration = new Configuration();
        Reflections reflections = new Reflections("e5.stateservice.examples.model");
        Set<Class<? extends E5State>> modelClasses = reflections.getSubTypesOf(E5State.class);

        // Add your POJOs to metadata sources programmatically
        modelClasses.forEach(modelClass -> configuration.addAnnotatedClass(modelClass));

        //configuration.addAnnotatedClass(Users.class); // Add all your entity classes here

        // Set properties
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:postgresql://localhost:5432/yourdb");
        configuration.setProperty("hibernate.connection.username", "postgres");
        configuration.setProperty("hibernate.connection.password", "pgadmin");
        configuration.setProperty("hibernate.default_schema", "schema1");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "update");
        configuration.setProperty("hibernate.show_sql", "true");

        ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties()).build();

        return configuration.buildSessionFactory(serviceRegistry);
    }

    public static <T extends E5State, F extends Enum<F> & E5FieldEnum> E5StateIterable<T, F> find(Class<T> entityClass, Class<F> FieldENum) {
        return new E5StateIterable<T, F>(entityClass, sessionFactory);
    }

    @Transactional
    public static <T extends E5State> void insertOne(T entity) {
        try (var session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.save(entity);
            session.getTransaction().commit();
        }
    }

    @Transactional
    public static <T extends E5State> void deleteOne(Class<T> entityClass, int id) {
        try (var session = sessionFactory.openSession()) {
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
        try (var session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.update(entity);
            session.getTransaction().commit();
        }
    }
}
