package e5.stateservice.service;

import e5.stateservice.model.E5State;
import e5.stateservice.model.E5DBServiceProperties;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.TargetDescriptor;
import org.reflections.Reflections;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class E5DBServiceInitializer {
    private static final String GRADLE_SETTINGS_FILE_NAME = "settings.gradle";
    private static final String ROOT_PROJECT_NAME = "rootProject.name";
    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.Driver";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";
    public static final String JDBC_POSTGRES_URL = "jdbc:postgresql://";
    public static final String JDBC_H2_URL = "jdbc:h2:mem:";
    public static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    public static final String H2_DIALECT = "org.hibernate.dialect.H2Dialect";

    public  static SessionFactory buildSessionFactory(E5DBServiceProperties dbServiceProps,
                                                      boolean allowSchemaChanges,
                                                      boolean isProd,
                                                      String packagePrefixToConsider) {
        Map<String, Object> settings = new HashMap<>();
        // Set properties
        if (isProd) {
            settings.put("hibernate.connection.driver_class", POSTGRES_DRIVER_CLASS);
            settings.put("hibernate.connection.url", JDBC_POSTGRES_URL + dbServiceProps.getEndpoint() + "/" + dbServiceProps.getDbName());
            settings.put("hibernate.connection.username", dbServiceProps.getDbUserName());
            settings.put("hibernate.connection.password", dbServiceProps.getDbPassword());
            settings.put("hibernate.default_schema", dbServiceProps.getSchemaName());
            settings.put("hibernate.dialect", POSTGRES_DIALECT);

        } else {
            settings.put("hibernate.connection.driver_class", H2_DRIVER_CLASS);
            settings.put("hibernate.connection.url", JDBC_H2_URL + dbServiceProps.getDbName());
            settings.put("hibernate.default_schema", dbServiceProps.getSchemaName());
            settings.put("hibernate.dialect", H2_DIALECT);
        }

        if (allowSchemaChanges) {
            settings.put("jakarta.persistence.schema-generation.database.action", "update");
        } else {
            settings.put("jakarta.persistence.schema-generation.database.action", "validate");
        }
        settings.put("hibernate.show_sql", "true");

        var serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings).build();
        Reflections reflections = new Reflections(packagePrefixToConsider);
        Set<Class<? extends E5State>> modelClasses = reflections.getSubTypesOf(E5State.class);

        try {
            if (allowSchemaChanges || canMakeSchemaChanges(settings, serviceRegistry, modelClasses)) {
                return getMetadata(serviceRegistry, modelClasses).buildSessionFactory();
            } else {
                throw new RuntimeException("Schema changes found!");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Metadata getMetadata(StandardServiceRegistry serviceRegistry, Set<Class<? extends E5State>> modelClasses) {
        var metadataSources = new MetadataSources(serviceRegistry);

        // Add your POJOs to metadata sources programmatically
        modelClasses.forEach(modelClass -> metadataSources.addAnnotatedClass(modelClass));

        var buildMetadata = metadataSources.buildMetadata();
        return buildMetadata;
    }

    private static ExecutionOptions getExecutionOptions(Map<String, Object> settings, List<String> schemaDifferences) {
        return new ExecutionOptions() {
            @Override
            public Map<String, Object> getConfigurationValues() {
                return settings;
            }

            @Override
            public boolean shouldManageNamespaces() {
                return true;
            }

            @Override
            public ExceptionHandler getExceptionHandler() {
                return (exception) -> {
                    schemaDifferences.add(exception.getMessage());
                    System.out.println("Schema difference detected: " + exception.getMessage());
                    if (exception != null) {
                        System.out.println("Associated exception: " + exception.getMessage());
                        exception.printStackTrace();
                    }
                };
            }
        };
    }

    private static ChangeResult isChangesPresent(Map<String, Object> settings, Metadata metadata, StandardServiceRegistry serviceRegistry) throws Exception {
        try {
            // Validate the schema
            SchemaManagementTool schemaManagementTool = serviceRegistry.getService(SchemaManagementTool.class);

            // Custom implementation to capture schema differences
            List<String> schemaDifferences = new ArrayList<>();

            // Validate the schema
            schemaManagementTool.getSchemaValidator(settings).doValidation(
                    metadata,
                    getExecutionOptions(settings, schemaDifferences),
                    ContributableMatcher.ALL
            );

            if (schemaDifferences.isEmpty()) {
                System.out.println("No schema differences detected.");
            } else {
                System.out.println("Schema differences detected:");
                return ChangeResult.builder().changesAvailable(true).result(new Exception(schemaDifferences.toString())).build();
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("missing table")) {
                return ChangeResult.builder().changesAvailable(true).result(e).build();
            }
        }
        return ChangeResult.builder().changesAvailable(false).build();
    }

    private static boolean canMakeSchemaChanges(Map<String, Object> settings, StandardServiceRegistry serviceRegistry, Set<Class<? extends E5State>> modelClasses) throws Exception {
        var buildMetadata = getMetadata(serviceRegistry, modelClasses);
        ChangeResult changeResult;
        if ((changeResult = isChangesPresent(settings, buildMetadata, serviceRegistry)).isChangesAvailable()) {
            throw new Exception("Schema changes not done!", ((Exception)changeResult.getResult()));
        }
        List<String> schemaDifferences = new ArrayList<>();
        SchemaManagementTool schemaManagementTool = serviceRegistry.getService(SchemaManagementTool.class);
        schemaManagementTool.getSchemaMigrator(settings).doMigration(
                buildMetadata,
                getExecutionOptions(settings, schemaDifferences),
                ContributableMatcher.ALL,
                new TargetDescriptor() {
                    @Override
                    public EnumSet<TargetType> getTargetTypes() {
                        return EnumSet.of(TargetType.DATABASE, TargetType.STDOUT);
                    }

                    @Override
                    public ScriptTargetOutput getScriptTargetOutput() {
                        return null;
                    }
                }
        );
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
