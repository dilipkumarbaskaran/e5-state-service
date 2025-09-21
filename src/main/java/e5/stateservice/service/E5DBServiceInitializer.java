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
                if (isProd) {
                    setupGuardrails(dbServiceProps.getSchemaName(), buildSessionFactory, modelClasses);
                }
            } else {
                throw new RuntimeException("Schema changes found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return buildSessionFactory;
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


    /**
     * Sets up database guardrails (triggers) to restrict updates or inserts on specified columns for certain tables.
     * <p>
     * This method reads a configuration file named <b>externally-non-editable-fields.json</b> from the resources directory,
     * which defines, for each table, a list of columns that should be protected from external modification.
     * For each table and its non-editable columns, this method creates or updates database triggers to enforce these restrictions.
     * If the configuration is empty, it checks for and removes any unnecessary guardrails that may already exist in the database.
     * <p>
     * The method performs the following steps:
     * <ol>
     *   <li>Loads the JSON configuration file from the classpath.</li>
     *   <li>Parses the file into a map of table names to lists of non-editable column names.</li>
     *   <li>If the configuration is empty, removes any existing guardrails from the database schema.</li>
     *   <li>Otherwise, removes unnecessary guardrails and sets up new triggers for each table/column as specified.</li>
     *   <li>Logs progress and errors throughout the process.</li>
     * </ol>
     *  @param schemaName         The name of the database schema where guardrails (triggers) should be managed.
     *
     * @param buildSessionFactory The Hibernate {@link SessionFactory} used to interact with the database.
     * @param modelClasses
     *
     *
     */
    public static void setupGuardrails(String schemaName, SessionFactory buildSessionFactory, Set<Class<? extends E5State>> modelClasses) {
        try (InputStream inputStream = E5DBServiceInitializer.class.getClassLoader()
                .getResourceAsStream("externally-non-editable-fields.json")) {
            if (inputStream == null) {
                throw new FileNotFoundException("File not found in resources!");
            }
            ObjectMapper mapper = new ObjectMapper();
            // Convert JSON into Map<String, List<String>>
            Map<String, List<String>> classWithNonEditableFieldsMap =
                    mapper.readValue(inputStream, new TypeReference<>() {
                    });
            removeUnnecessaryGuardrails(modelClasses, buildSessionFactory);
            if (classWithNonEditableFieldsMap.isEmpty()) {
                logger.info("No guard specified in schema, checking for any guardrails if already present in DB!!!");
            } else {
                logger.info("Setting up guardrails for the following tables: {}", classWithNonEditableFieldsMap.keySet());
                for (Map.Entry<String, List<String>> entry : classWithNonEditableFieldsMap.entrySet()) {
                    String key = entry.getKey();
                    List<String> value = entry.getValue();
                    logger.info("Processing table: {} with non-editable columns: {}", key, value);
                    buildTriggers(value, key, schemaName, buildSessionFactory);
                }
                logger.info("Setting up guardrails Complete. Now checking for unnecessary guardrails if already present!!!");
            }
        } catch (FileNotFoundException e){
            logger.error("externally-non-editable-fields.json does not exist", e);
        }
        catch (Exception e) {
            logger.error("Exception occurred while setting up guardrails", e);
            throw new RuntimeException("Exception occurred while setting up guardrails", e);
        }
    }

    /**
     * Removes unnecessary guardrails (triggers) from the database schema.
     * <p>
     * This method removes all the guard rails currently present in the DB.
     * <ul>
     *   <li>Fetches all tables with triggers in the given schema.</li>
     *   <li>Identifies tables that no longer require guardrails by comparing with editableFields.</li>
     *   <li>Drops triggers for those tables to keep the schema clean and up-to-date.</li>
     * </ul>
     *
     * @param stateClasses         Map of table names to their editable fields, as defined in the guardrails config.
     * @param buildSessionFactory    The Hibernate SessionFactory to use for DB access.
     * @author vishal-e5
     * @date 2024-06-09
     */
    private static void removeUnnecessaryGuardrails(Set<Class<? extends E5State>> stateClasses, SessionFactory buildSessionFactory) {
        List<String> stateClassList = stateClasses.stream()
                .map(Class::getSimpleName)
                .map(String::toLowerCase)
                .toList();
        try(Session session = buildSessionFactory.openSession()){
            session.doWork(connection -> {
                if (stateClassList.isEmpty()) {
                    logger.info("No State Classes Exists");
                }
                logger.info("Deleting all the triggers from state tables before deployment : {}", stateClassList);
                String tablesToRemoveGuardrailsStr = stateClassList.stream()
                        .map(table -> "'" + table.replace("'", "''") + "'")
                        .collect(java.util.stream.Collectors.joining(","));
                String deleteUnnecessaryTriggers = """
                            DO $$
                            DECLARE
                                trigger_rec RECORD;
                            BEGIN
                                FOR trigger_rec IN (
                                    SELECT trigger_name, event_object_table
                                    FROM information_schema.triggers
                                    WHERE event_object_table IN (%s) -- Replace with your table names
                                )
                                LOOP
                                    EXECUTE 'DROP TRIGGER IF EXISTS ' || quote_ident(trigger_rec.trigger_name) || ' ON ' || quote_ident(trigger_rec.event_object_table) || ';';
                                END LOOP;
                            END $$;
                            """.formatted(tablesToRemoveGuardrailsStr);

                try (PreparedStatement statement = connection.prepareStatement(deleteUnnecessaryTriggers)) {
                    statement.executeQuery();
                    logger.info("Triggers removed for all tables before deployment {}", stateClassList);
                } catch (SQLException e) {
                    logger.error("Failed to delete trigger");
                    throw new RuntimeException("Failed to delete trigger: " + e.getMessage(), e);
                }

            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds triggers to the table schema for the specified entity tables.
     *
     * <p>
     * This method will add triggers to the table schema. It takes as input a list of entity table names.
     * </p>
     *
     * @param tableColumnNames a list of entity table names to which triggers will be added
     * @param tableName       the name of the class to which the triggers will be added
     * @param sessionFactory
     */
    public static void buildTriggers(List<String> tableColumnNames, String tableName, String schemaName, SessionFactory sessionFactory) {
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
                + buildUpdateTriggerCondition(tableColumnNames)
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
        createOrManageTrigger(tableName, functionName, sessionFactory);

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


    private static void createOrManageTrigger(String tableName, String functionName, SessionFactory sessionfactory) {
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
            logger.info("Error while creating trigger", e);
            throw new RuntimeException("Error while creating trigger", e);
        }
    }
}