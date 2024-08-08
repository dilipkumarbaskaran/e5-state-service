package e5.stateservice.service;

import e5.stateservice.model.E5State;
import e5.stateservice.model.E5StateServiceProperties;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaUpdate;
import org.hibernate.tool.schema.TargetType;
import org.reflections.Reflections;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

public class E5StateServiceInitializer {
    protected static SessionFactory sessionFactory;
    private static final String GRADLE_SETTINGS_FILE_NAME = "settings.gradle";
    private static final String ROOT_PROJECT_NAME = "rootProject.name";
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    public static final String JDBC_POSTGRES_URL = "jdbc:postgresql://";
    public static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";

    public static void init (E5StateServiceProperties stateServiceProps) {
        sessionFactory = buildSessionFactory(stateServiceProps);
    }

    private static SessionFactory buildSessionFactory(E5StateServiceProperties stateServiceProps) {
        Configuration configuration = new Configuration();

        // Set properties
        configuration.setProperty("hibernate.connection.driver_class", POSTGRES_DRIVER);
        configuration.setProperty("hibernate.connection.url", JDBC_POSTGRES_URL + stateServiceProps.getEndpoint() + "/" + stateServiceProps.getDbName());
        configuration.setProperty("hibernate.connection.username", stateServiceProps.getDbUserName());
        configuration.setProperty("hibernate.connection.password", stateServiceProps.getDbPassword());
        configuration.setProperty("hibernate.default_schema", stateServiceProps.getSchemaName());
        configuration.setProperty("hibernate.dialect", POSTGRES_DIALECT);
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        configuration.setProperty("hibernate.show_sql", "true");

        var serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(configuration.getProperties()).build();
        Reflections reflections = new Reflections("e5."+getAppName().toLowerCase()+".model.state");
        Set<Class<? extends E5State>> modelClasses = reflections.getSubTypesOf(E5State.class);

        try {
            if (canMakeSchemaChanges(serviceRegistry, modelClasses)) {
                // Add your POJOs to metadata sources programmatically
                modelClasses.forEach(modelClass -> configuration.addAnnotatedClass(modelClass));
                var registry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties()).build();

                return configuration.buildSessionFactory(registry);
            } else {
                throw new RuntimeException("Schema changes found!");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ChangeResult isChangesPresent(Metadata metadata) throws Exception {
        try {
            // Validate the schema
            new org.hibernate.tool.hbm2ddl.SchemaValidator().validate(metadata);

            // Validate the schema
            new SchemaUpdate()
                    .setHaltOnError(true)
                    .setFormat(true)
                    .setDelimiter(";")
                    .execute(EnumSet.of(TargetType.DATABASE), metadata);

            //metadata.buildSessionFactory();
            metadata.getSessionFactoryBuilder().build();
        } catch (Exception e) {
            if (!e.getMessage().contains("missing table")) {
                return ChangeResult.builder().changesAvailable(true).result(e).build();
            }
        }
        return ChangeResult.builder().changesAvailable(false).build();
    }

    private static boolean canMakeSchemaChanges(StandardServiceRegistry serviceRegistry, Set<Class<? extends E5State>> modelClasses) throws Exception {
        var metadataSources = new MetadataSources(serviceRegistry);


        // Add your POJOs to metadata sources programmatically
        modelClasses.forEach(modelClass -> metadataSources.addAnnotatedClass(modelClass));

        var buildMetadata = metadataSources.buildMetadata();
        ChangeResult changeResult;
        if ((changeResult = isChangesPresent(buildMetadata)).isChangesAvailable()) {
            //System.out.println("Changes not done");
            throw new Exception("Schema changes not done!", ((Exception)changeResult.getResult()));
        }
        var schemaExport = new org.hibernate.tool.hbm2ddl.SchemaExport();
        //schemaExport.perform(SchemaExport.Action.BOTH, metadata, new ScriptTargetOutputToFile(new File("/home/dilipkumar.baskaran/IdeaProjects/SQLGenerationPOC/src/main/resources/ddl.sql"), "US-ASCII"));
        schemaExport.setDelimiter(";");
        //schemaExport.setOutputFile("src/main/resources/ddl.sql");
        schemaExport.createOnly(EnumSet.of(TargetType.DATABASE,TargetType.STDOUT), buildMetadata);
        return true;
    }

    protected static String getAppName() {
        String settingsFilePath = GRADLE_SETTINGS_FILE_NAME;
        try (FileReader fileReader = new FileReader(settingsFilePath); BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.trim().startsWith(ROOT_PROJECT_NAME)) {
                    return line.trim().split("=")[1].replace("'", "").trim().replaceAll("[^A-Za-z0-9]","").toLowerCase();
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "";
    }
}
