package e5.stateservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import e5.stateservice.model.E5DBServiceProperties;
import e5.stateservice.model.E5State;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.spi.*;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class E5DBServiceInitializer {
    private static final String GRADLE_SETTINGS_FILE_NAME = "settings.gradle";
    private static final String ROOT_PROJECT_NAME = "rootProject.name";
    private static final String POSTGRES_DRIVER_CLASS = "org.postgresql.Driver";
    private static final String H2_DRIVER_CLASS = "org.h2.Driver";
    public static final String JDBC_POSTGRES_URL = "jdbc:postgresql://";
    public static final String JDBC_H2_URL = "jdbc:h2:mem:";
    public static final String POSTGRES_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
    public static final String H2_DIALECT = "org.hibernate.dialect.H2Dialect";
    private static final Logger logger = LoggerFactory.getLogger(E5DBServiceInitializer.class);

    public static SessionFactory buildSessionFactory(E5DBServiceProperties dbServiceProps,
                                                     boolean allowSchemaChanges,
                                                     boolean isProd,
                                                     String packagePrefixToConsider) {
        Map<String, Object> settings = new HashMap<>();
        // Set properties
        if (allowSchemaChanges) {
            settings.put("jakarta.persistence.schema-generation.database.action", "update");
        } else {
            settings.put("jakarta.persistence.schema-generation.database.action", "validate");
        }

        // apply custom Hibernate configuration
        applyCustomHibernateConfig(settings, dbServiceProps.getDbProperties());

        if (isProd) {
            settings.put("hibernate.connection.driver_class", POSTGRES_DRIVER_CLASS);
            settings.put("hibernate.connection.url", JDBC_POSTGRES_URL + dbServiceProps.getEndpoint() + "/" + dbServiceProps.getDbName());
            settings.put("hibernate.connection.username", dbServiceProps.getDbUserName());
            settings.put("hibernate.connection.password", dbServiceProps.getDbPassword());
            settings.put("hibernate.default_schema", dbServiceProps.getSchemaName());
            settings.put("hibernate.dialect", POSTGRES_DIALECT);

        } else {
            settings.put("hibernate.connection.driver_class", H2_DRIVER_CLASS);
            settings.put("hibernate.connection.url", JDBC_H2_URL + dbServiceProps.getDbName() + ";INIT=CREATE SCHEMA IF NOT EXISTS " + dbServiceProps.getSchemaName() + ";");
            settings.put("hibernate.default_schema", dbServiceProps.getSchemaName());
            settings.put("jakarta.persistence.schema-generation.database.action", "create-drop");
        }

        var serviceRegistry = new StandardServiceRegistryBuilder()
                .applySettings(settings).build();
        Reflections reflections = new Reflections(packagePrefixToConsider);
        Set<Class<? extends E5State>> modelClasses = reflections.getSubTypesOf(E5State.class);
        SessionFactory buildSessionFactory = null;
        try {
            if (allowSchemaChanges || canMakeSchemaChanges(settings, serviceRegistry, modelClasses)) {
                buildSessionFactory = getMetadata(serviceRegistry, modelClasses).buildSessionFactory();

                setupGuardrails(dbServiceProps.getSchemaName(), buildSessionFactory);
            } else {
                throw new RuntimeException("Schema changes found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return buildSessionFactory;
    }

    public static void setupGuardrails(String schemaName, SessionFactory buildSessionFactory) {
        try (InputStream inputStream = E5DBServiceInitializer.class.getClassLoader()
                .getResourceAsStream("externally-non-editable-fields.json")) {
            if (inputStream == null) {
                throw new RuntimeException("File not found in resources!");
            }
            ObjectMapper mapper = new ObjectMapper();
            // Convert JSON into Map<String, List<String>>
            Map<String, List<String>> editableFields =
                    mapper.readValue(inputStream, new TypeReference<>() {
                    });
            if (editableFields.isEmpty()) {
                logger.info("None of the Schemas has guardrails set");
            } else {
                for (Map.Entry<String, List<String>> entry : editableFields.entrySet()) {
                    String key = entry.getKey();
                    List<String> value = entry.getValue();
                    logger.info("Processing table: {} with non-editable columns: {}", key, value);
                    buildTriggers(value, key, schemaName, buildSessionFactory);
                }
            }
        } catch (Exception e) {
            logger.error("externally-non-editable-fields.json does not exist", e);
        }
    }

    /**
     * This method is used to apply custom Hibernate configuration.
     * It reads the configuration from the .env file and applies it to the settings map.
     *
     * @param settings       The settings map to which the custom configuration will be applied.
     * @param dbServiceProps The database service properties containing the configuration.
     */
    private static void applyCustomHibernateConfig(Map<String, Object> settings, Properties dbServiceProps) {
        final String DEFAULT_QUERY_PLAN_CACHE_MAX_SIZE = "1";
        final String DEFAULT_SHOW_SQL = "false";
        final String DEFAULT_IN_CLAUSE_PARAM_PADDING = "true";
        final String DEFAULT_MINIMUM_IDLE = "0";
        final String DEFAULT_IDLE_TIMEOUT = "300000";
        final String DEFAULT_CONNECTION_TIMEOUT = "30000";
        final String DEFAULT_MAXIMUM_POOL_SIZE = "5";

        //Get queryPlanCacheMaxSize property from dbProperties
        String queryPlanCacheMaxSize = getValidatedProperty(
                dbServiceProps,
                "queryPlanCacheMaxSize",
                DEFAULT_QUERY_PLAN_CACHE_MAX_SIZE,
                new Validator<Integer>() {
                    @Override
                    public Integer parse(String value) {
                        return Integer.parseInt(value);
                    }

                    @Override
                    public boolean isValid(Integer value) {
                        return value > 0;
                    }
                }
        );
        settings.put("hibernate.query.plan_cache_max_size", queryPlanCacheMaxSize);

        // Get showSql property from dbProperties
        String showSql = getValidatedProperty(
                dbServiceProps,
                "showSql",
                DEFAULT_SHOW_SQL,
                new Validator<String>() {
                    @Override
                    public String parse(String value) {
                        return value;
                    }

                    @Override
                    public boolean isValid(String value) {
                        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                    }
                }
        );
        settings.put("hibernate.show_sql", showSql);

        // Get inClauseParameterPadding property from dbProperties
        String inClauseParameterPadding = getValidatedProperty(
                dbServiceProps,
                "inClauseParameterPadding",
                DEFAULT_IN_CLAUSE_PARAM_PADDING,
                new Validator<String>() {
                    @Override
                    public String parse(String value) {
                        return value;
                    }

                    @Override
                    public boolean isValid(String value) {
                        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
                    }
                }
        );
        settings.put("hibernate.query.in_clause_parameter_padding", inClauseParameterPadding);

        // Get minimumIdle property from dbProperties
        String minimumIdle = getValidatedProperty(
                dbServiceProps,
                "minimumIdle",
                DEFAULT_MINIMUM_IDLE,
                new Validator<Integer>() {
                    @Override
                    public Integer parse(String value) {
                        return Integer.parseInt(value);
                    }

                    @Override
                    public boolean isValid(Integer value) {
                        return value >= 0;
                    }
                }
        );
        settings.put("hibernate.hikari.minimumIdle", minimumIdle);

        // Get idleTimeout property from dbProperties
        String idleTimeout = getValidatedProperty(
                dbServiceProps,
                "idleTimeout",
                DEFAULT_IDLE_TIMEOUT,
                new Validator<Long>() {
                    @Override
                    public Long parse(String value) {
                        return Long.parseLong(value);
                    }

                    @Override
                    public boolean isValid(Long value) {
                        return value >= 0;
                    }
                }
        );
        settings.put("hibernate.hikari.idleTimeout", idleTimeout);

        // Get connectionTimeout property from dbProperties
        String connectionTimeout = getValidatedProperty(
                dbServiceProps,
                "connectionTimeout",
                DEFAULT_CONNECTION_TIMEOUT,
                new Validator<Long>() {
                    @Override
                    public Long parse(String value) {
                        return Long.parseLong(value);
                    }

                    @Override
                    public boolean isValid(Long value) {
                        return value >= 0;
                    }
                }
        );
        settings.put("hibernate.hikari.connectionTimeout", connectionTimeout);

        // Get maximumPoolSize property from dbProperties
        String maximumPoolSize = getValidatedProperty(
                dbServiceProps,
                "maximumPoolSize",
                DEFAULT_MAXIMUM_POOL_SIZE,
                new Validator<Integer>() {
                    @Override
                    public Integer parse(String value) {
                        return Integer.parseInt(value);
                    }

                    @Override
                    public boolean isValid(Integer value) {
                        return value > 0;
                    }
                }
        );
        settings.put("hibernate.hikari.maximumPoolSize", maximumPoolSize);

        logger.info("Custom Hibernate configuration applied: [hibernate.query.plan_cache_max_size]={}, [hibernate.show_sql]={}, " +
                        "[hibernate.query.in_clause_parameter_padding]={}, [hibernate.hikari.minimumIdle]={}, [hibernate.hikari.idleTimeout]={}, " +
                        "[hibernate.hikari.connectionTimeout]={}, [hibernate.hikari.maximumPoolSize]={}", queryPlanCacheMaxSize, showSql,
                inClauseParameterPadding, minimumIdle, idleTimeout, connectionTimeout, maximumPoolSize);

    }

    /**
     * This method retrieves a property from the database properties, validates it, and applies a default value if necessary.
     *
     * @param dbProperties The database properties.
     * @param propertyName The name of the property to retrieve.
     * @param defaultValue The default value to use if the retrieved value is invalid.
     * @param validator    A functional interface to validate the property value.
     * @param <T>          The type of the property value.
     * @return The valid property value.
     */
    private static <T> String getValidatedProperty(Properties dbProperties, String propertyName, String defaultValue, Validator<T> validator) {
        if (dbProperties == null) {
            return defaultValue;
        }
        String propertyValue = dbProperties.getProperty(propertyName, defaultValue.toString());
        try {
            T parsedValue = validator.parse(propertyValue);
            if (!validator.isValid(parsedValue)) {
                logger.warn("Invalid {} value: {}. Defaulting to {}.", propertyName, propertyValue, defaultValue);
                return defaultValue;
            }
            return String.valueOf(parsedValue);
        } catch (Exception e) {
            logger.error("Incorrect {} value: {}. Defaulting to {}.", propertyName, propertyValue, defaultValue, e);
            return defaultValue;
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
            throw new Exception("Schema changes not done!", ((Exception) changeResult.getResult()));
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
                    return line.trim().split("=")[1].replace("'", "").trim().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return "";
    }

    /**
     * Adds triggers to the table schema for the specified entity tables.
     *
     * <p>
     * This method will add triggers to the table schema. It takes as input a list of entity table names.
     * </p>
     *
     * @param tableColumnName a list of entity table names to which triggers will be added
     * @param tableName       the name of the class to which the triggers will be added
     * @param sessionFactory
     */
    public static void buildTriggers(List<String> tableColumnName, String tableName, String schemaName, SessionFactory sessionFactory) {
        String functionName = tableName + "_trigger_function";
        logger.info("Creating trigger function for table {} -> {}", tableName, functionName);
        String triggerSql = String.format("""
                CREATE OR REPLACE FUNCTION %s
                RETURNS TRIGGER AS $$
                BEGIN
                    -- Check if bypass is enabled; default to restricted if unset
                    IF coalesce(current_setting('app_context.allow_restricted', true), 'false') != 'true' THEN
                        IF TG_OP = 'INSERT' THEN
                            RAISE EXCEPTION 'Cannot insert new records. Access Restricted by workflow';
                        ELSIF TG_OP = 'UPDATE' THEN
                            -- Check if restricted columns are being changed
                """
                + buildUpdateTriggerCondition(tableColumnName)
                + """
                                RAISE EXCEPTION 'Cannot update person_id, person_name, or person_nationality. Access Restriced by workflow.';
                            END IF;
                        END IF;
                    END IF;
                    RETURN NEW; 
                END;
                $$ LANGUAGE plpgsql;
                """, schemaName.concat("." + functionName + "()"));
        Session session = null;
        try {
            session = sessionFactory.openSession();
            session.doWork(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(triggerSql)) {
                    statement.executeUpdate();
                    logger.info("Successfully created trigger function: {}", functionName);

                } catch (SQLException e) {
                    logger.error("Failed to create trigger function: {}", functionName, e);
                    throw new RuntimeException("Error creating trigger function: " + e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            logger.error("Failed to create trigger function: {}", functionName, e);
            throw new RuntimeException("Error creating trigger function: " + e.getMessage(), e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
        createOrManageTrigger(tableName, schemaName, functionName, sessionFactory);

    }

    private static String buildUpdateTriggerCondition(List<String> tableColumnName) {
        StringBuilder conditionBuilder = new StringBuilder();
        for (int i = 0; i < tableColumnName.size(); i++) {
            String columnName = tableColumnName.get(i);
            conditionBuilder.append("NEW.").append(columnName).append(" IS DISTINCT FROM OLD.").append(columnName);
            if (i < tableColumnName.size() - 1) {
                conditionBuilder.append(" OR ");
            }
        }
        return """   
                IF\t"""
                + conditionBuilder.toString() +
                """
                          THEN
                        """;
    }


    private static void createOrManageTrigger(String tableName, String schemaName, String functionName, SessionFactory sessionfactory) {
        logger.info("Attaching trigger function {} to table {}", functionName, tableName);
        String triggerName = tableName + "_trigger";
        try (Session session = sessionfactory.openSession()) {
            session.doWork(connection -> {


                String createTriggerSql = String.format(
                        "CREATE OR REPLACE  TRIGGER %s BEFORE UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION %s();",
                        triggerName, tableName, functionName
                );

                try (PreparedStatement createStatement = connection.prepareStatement(createTriggerSql)) {
                    createStatement.executeUpdate();
                    logger.info("Successfully updated trigger: {}", triggerName);
                } catch (SQLException e) {
                    logger.error("Failed to manage trigger: {}", triggerName, e);
                    throw new RuntimeException("Error managing trigger: " + e.getMessage(), e);
                }
            });
        } catch (HibernateException e) {
            throw new RuntimeException(e);
        }
    }
}