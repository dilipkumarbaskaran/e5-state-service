package e5.stateservice.service;

import e5.stateservice.model.E5State;
import e5.stateservice.model.state.Users;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class E5DBServiceInitializerTest {


    private static final Logger logger = LoggerFactory.getLogger(E5DBServiceInitializerTest.class);

    private static final String GRADLE_SETTINGS_FILE_NAME = "settings.gradle";
    private static final String ROOT_PROJECT_NAME = "rootProject.name";
    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.Driver";
    public static final String JDBC_POSTGRES_URL = "jdbc:postgresql://";
    public static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";

    
    private SessionFactory getSessionFactory(Class<? extends E5State> clazz, TestDBProps props) {
        // Create a custom SessionFactory for PostgreSQL using Hibernate (do not reuse existing method)
       Map<String, Object> settings = new HashMap<>();
       settings.put("hibernate.connection.driver_class", POSTGRES_DRIVER_CLASS);
       settings.put("hibernate.connection.url", JDBC_POSTGRES_URL + props.dbEndpoint() + "/" + props.dbName());
       settings.put("hibernate.connection.username", props.dbUserName());
       settings.put("hibernate.connection.password", props.dbPassword());
       settings.put("hibernate.default_schema", props.schemaName());
       settings.put("hibernate.dialect", POSTGRES_DIALECT);
       settings.put("hibernate.hbm2ddl.auto", "update");
       settings.put("hibernate.show_sql", props.customProperties().getProperty("showSql", "true"));

       // Build the service registry
       StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
               .applySettings(settings)
               .build();

       // You must add your annotated entity classes here. For demonstration, using E5State.class.
       MetadataSources metadataSources = new MetadataSources(serviceRegistry);
       metadataSources.addAnnotatedClass(clazz); // Add more entities as needed

       Metadata metadata = metadataSources.buildMetadata();

       return metadata.buildSessionFactory();

       }

    private static TestDBProps getProperties() {
        java.util.Properties customProperties = new java.util.Properties();
        String dbEndpoint = "localhost:5432";
        String dbName = "postgres";
        String schemaName = "public";
        String dbUserName = "postgres";
        String dbPassword = "admin";
        TestDBProps props = new TestDBProps(customProperties, dbEndpoint, dbName, schemaName, dbUserName, dbPassword);
        return props;
    }

    private record TestDBProps(java.util.Properties customProperties, String dbEndpoint, String dbName, String schemaName, String dbUserName, String dbPassword) {
    }

    @Test
    public void shouldCreateGuardrailsTest(){
        TestDBProps props = getProperties();
        SessionFactory sessionFactory = getSessionFactory(Users.class, props);
        E5DBServiceInitializer.setupGuardrails(props.schemaName(), sessionFactory);

    }

}
