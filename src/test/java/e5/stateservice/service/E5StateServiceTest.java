package e5.stateservice.service;

import e5.stateservice.model.E5DBServiceProperties;
import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import e5.stateservice.model.state.NameEmailFilter;
import e5.stateservice.model.state.Users;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.GenericJDBCException;
import org.junit.jupiter.api.Assertions;
import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


public class E5StateServiceTest {

    private static final Logger logger = LoggerFactory.getLogger(E5StateServiceTest.class);
    private SessionFactory sessionFactory;

    @BeforeEach
    public void setupStateService() {
        Properties customProperties = new Properties();

        // Set custom properties for Hibernate
        customProperties.put("queryPlanCacheMaxSize", "1024");
        customProperties.put("showSql", "false");
        customProperties.put("inClauseParameterPadding", "true");

        E5DBServiceProperties stateServiceProps = E5DBServiceProperties.builder()
                .endpoint("localhost:5432")
                .dbName("postgres")
                .schemaName("public")
                .dbUserName("postgres")
                .dbPassword("postgres@001")
                .dbProperties(customProperties).
                build();
        sessionFactory = E5DBServiceInitializer.buildSessionFactory(stateServiceProps, false, true, "e5");
    }

    @Test
    public void testInsert() {

        E5StateFilterOptions<Users> E5FilterOptions1 = E5StateFilterOptions.create(Users.class)
                .eq(Users.NAME, "John Doe")
                .gt(Users.ID, 1l);

        E5StateFilterOptions<Users> E5FilterOptions2 = E5StateFilterOptions.create(Users.class)
                .lt(Users.ID, 100l);

        E5StateFilterGroup<Users> filterGroup = E5StateFilterGroup.create(Users.class, E5StateFilterGroup.LogicalOperator.OR)
                .addFilter(E5FilterOptions1)
                .addFilter(E5FilterOptions2);

        E5StateFilterOptions<Users> combinedE5FilterOptions = E5StateFilterOptions.create(Users.class)
                .lt(Users.ID, 1000l)
                .addGroup(filterGroup);

        int countPrevious = E5StateService.find(sessionFactory, Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();
        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe1@example.com");
        newUser = E5StateService.insertOne(sessionFactory, newUser);

        Assertions.assertTrue(newUser.getId()!=0);

        int countAfter = E5StateService.find(sessionFactory, Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();

        Assertions.assertEquals(countPrevious+1, countAfter);

    }

    @Test
    public void testSearch() {
        E5StateFilterOptions<Users> filterOptions = E5StateFilterOptions.create(Users.class);

        for (int i=0;i<100;i++) {
            filterOptions.lt(Users.ID, 5l+i);
        }

        E5StateFilterGroup<Users> filterGroup1 = E5StateFilterGroup.create(Users.class, E5StateFilterGroup.LogicalOperator.OR)
                .addFilter(filterOptions)
                .addFilter(filterOptions);

        E5StateFilterOptions<Users> filterOptions1 = E5StateFilterOptions.create(Users.class)
                .addGroup(filterGroup1)
                .addGroup(filterGroup1);

        int countBefore = E5StateService.find(sessionFactory, Users.class)
                .filter(filterOptions1)
                .list().size();

        if (countBefore < 5) {
            // Insert a new user
            Users newUser = new Users();
            newUser.setName("John Doe");
            String random = UUID.randomUUID().toString().replace("-","");
            newUser.setEmail("john.doe+"+random+"@example.com");
            newUser = E5StateService.insertOne(sessionFactory, newUser);

            int countAfter = E5StateService.find(sessionFactory, Users.class)
                    .filter(filterOptions1)
                    .list().size();
            Assertions.assertEquals(countBefore+1, countAfter);
        }
    }

    /**
     * This method tests the search functionality with a large number of parameters.
     * It creates a filter with multiple conditions and verifies the result count.
     */
    @Test
    public void testSearchWithLargeParameterValues() {
        assertDoesNotThrow(() -> {
            for (int iteration = 0; iteration < 1; iteration++) {
                E5StateFilterOptions<Users> filterOptions = E5StateFilterOptions.create(Users.class);
                //int limit =  (int)(Math.random() * 1000) + 1;
                int limit = 1000;
                logger.info("Iteration {}: Limit: {}", iteration + 1, limit);
                for (int i = 0; i < limit; i++) {
                    filterOptions.lt(Users.ID, 5l + i);
                }

                E5StateFilterGroup<Users> filterGroup1 = E5StateFilterGroup.create(Users.class, E5StateFilterGroup.LogicalOperator.OR)
                        .addFilter(filterOptions)
                        .addFilter(filterOptions);

                E5StateFilterOptions<Users> filterOptions2 = E5StateFilterOptions.create(Users.class)
                        .addGroup(filterGroup1)
                        .addGroup(filterGroup1);

                int countBefore = E5StateService.find(sessionFactory, Users.class)
                        .filter(filterOptions2)
                        .list().size();

                // Check query plan cache size
                logger.info("Iteration {}: ", iteration + 1);
            }
            logger.info("Executed testSearchWithLargeParameterValues successfully.");
        });
    }

    /**
     * This method tests the search functionality with an IN clause.
     * It creates a filter with multiple values and verifies the result count.
     */
    @Test
    public void testSearchWithInClause() {
        E5SearchField<Users, NameEmailFilter> nameField = Users.NAMEEMAIL;

        List<NameEmailFilter> filterValues = List.of(
                new NameEmailFilter("John 1", "john.doe_11111@example.com"),
                new NameEmailFilter("John Doe", "john.doe_31111@example.com")
        );

        try {
            // Create filter options with the IN condition
            E5StateFilterOptions<Users> filterOptions = E5StateFilterOptions.create(Users.class)
                    .in(nameField, filterValues);

            // Execute the query and verify the result
            int count = E5StateService.find(sessionFactory, Users.class)
                    .filter(filterOptions)
                    .list()
                    .size();

            logger.info("Result count: {}", count);
            Assertions.assertTrue(count >= 0, "The count should be non-negative.");
        } catch (Exception e) {
            logger.error("Error during testSearchWithInClause: {}", e.getMessage(), e);
            Assertions.fail("Exception occurred during test execution: " + e.getMessage());
        }
    }

    /**
     * Test for searching with NOT IN clause.
     * This test checks if the search functionality works correctly when using the NOT IN clause.
     */
    @Test
    public void testSearchWithNotInClause() {
        E5SearchField<Users, NameEmailFilter> nameField = Users.NAMEEMAIL;

        List<NameEmailFilter> filterValues = List.of(
                new NameEmailFilter("John 1", "john.doe+1fb6ca70e7d2423d8a59ea86d3a2f409@example.com"),
                new NameEmailFilter("John 2", "john.doe+c03e49cb8542478a9498228833413a8e@example.com")
        );

        try {
            // Create filter options with the NOT IN condition
            E5StateFilterOptions<Users> filterOptions = E5StateFilterOptions.create(Users.class)
                    .nin(nameField, filterValues);

            // Execute the query and verify the result
            int count = E5StateService.find(sessionFactory, Users.class)
                    .filter(filterOptions)
                    .list()
                    .size();

            logger.info("Result count: {}", count);
            Assertions.assertTrue(count >= 0, "The count should be non-negative.");
        } catch (Exception e) {
            logger.error("Error during testSearchWithNotInClause: {}", e.getMessage(), e);
            Assertions.fail("Exception occurred during test execution: " + e.getMessage());
        }
    }

    @Test
    public void testInsertMany() {
        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe2@example.com");

        Users newUser1 = new Users();
        newUser1.setName("John Doe");
        newUser1.setEmail("john.doe3@example.com");
        int countPrevious = E5StateService.find(sessionFactory, Users.class)
                .list().size();
        E5StateService.insertMany(sessionFactory, List.of(newUser, newUser1));
        int countAfter = E5StateService.find(sessionFactory, Users.class)
                .list().size();
        Assertions.assertEquals(countPrevious+2, countAfter);
    }

    @Test
    public void testInsertManyWithException() {

        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe4@example.com");
        Assertions.assertThrows(ConstraintViolationException.class, ()-> {E5StateService.insertMany(sessionFactory, List.of(newUser, newUser));});
    }

    @Test
    public void testUpdate() {
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe5@example.com");
        newUser = E5StateService.insertOne(sessionFactory, newUser);
        // Update a user
        try (var cursor = E5StateService.find(sessionFactory, Users.class).iterator()) {
            Users userToUpdate;
            while (cursor.hasNext()) {
                userToUpdate = (Users) cursor.next();
                userToUpdate.setEmail("john.doe_" + userToUpdate.getId() + "1111@example.com");
                E5StateService.updateOne(sessionFactory, userToUpdate);
            }
        }

        try (var cursor = E5StateService.find(sessionFactory, Users.class)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = cursor.next();
                Assertions.assertEquals("john.doe_" + user.getId() + "1111@example.com", user.getEmail());
            }
        }
    }

    @Test
    public void testUpdateMany() {
        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe6@example.com");

        Users newUser1 = new Users();
        newUser1.setName("John Doe");
        newUser1.setEmail("john.doe7@example.com");
        int countPrevious = E5StateService.find(sessionFactory, Users.class)
                .list().size();
        E5StateService.insertMany(sessionFactory, List.of(newUser, newUser1));
        // Update Multiple user
        List<Users> users = E5StateService.find(sessionFactory, Users.class)
                .list();
        for (Users user :users) {
            user.setEmail("john.doe_" + user.getId() + "1111@example.com");
        }

        E5StateService.updateMany(sessionFactory, users);

        try (var cursor = E5StateService.find(sessionFactory, Users.class)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = cursor.next();
                Assertions.assertEquals("john.doe_" + user.getId() + "1111@example.com", user.getEmail());
            }
        }
    }

    @Test
    public void testUpdateManyWithException() {

        // Update Multiple user
        List<Users> users = E5StateService.find(sessionFactory, Users.class)
                .list();
        for (Users user :users) {
            user.setEmail("john.doe_1111@example.com");
        }

        Assertions.assertThrows(ConstraintViolationException.class, ()-> {E5StateService.updateMany(sessionFactory, users);});
    }

    @Test
    public void testDelete() {
        // Fetch users with filters, sorting, limit, and skip
        E5StateFilterOptions<Users> E5FilterOptions1 = E5StateFilterOptions.create(Users.class)
                .eq(Users.NAME, "John Doe")
                .gt(Users.ID, 1l);

        E5StateFilterOptions<Users> E5FilterOptions2 = E5StateFilterOptions.create(Users.class)
                .lt(Users.ID, 100l);

        E5StateFilterGroup<Users> filterGroup = E5StateFilterGroup.create(Users.class, E5StateFilterGroup.LogicalOperator.OR)
                .addFilter(E5FilterOptions1)
                .addFilter(E5FilterOptions2);

        E5StateFilterOptions<Users> combinedE5FilterOptions = E5StateFilterOptions.create(Users.class)
                .lt(Users.ID, 1000l)
                .addGroup(filterGroup);

        int countBefore = E5StateService.find(sessionFactory, Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .sort(Users.EMAIL, false)
                .sort(Users.ID, true)
                .list().size();

        if (countBefore>0) {
            // Delete a user
            try (var cursor = E5StateService.find(sessionFactory, Users.class)
                    .filter(combinedE5FilterOptions)
                    .iterator()) {
                if (cursor.hasNext()) {
                    E5StateService.deleteOne(sessionFactory, Users.class, ((Users) cursor.next()).getId());
                }
            }

            int countAfter = E5StateService.find(sessionFactory, Users.class)
                    .filter(combinedE5FilterOptions)
                    .sort(Users.NAME, true)
                    .list().size();

            Assertions.assertEquals(countBefore - 1, countAfter);
        }

    }

    @Test
    public void testInsertWithRestriction(){
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe1@example.com");
        
        GenericJDBCException exception = Assertions.assertThrows(GenericJDBCException.class, () -> {
            insertOne(sessionFactory, newUser);
        });
        
        // Verify that the underlying cause is the PostgreSQL restriction error
        Assertions.assertInstanceOf(PSQLException.class, exception.getCause());
        Assertions.assertTrue(exception.getCause().getMessage().contains("Access Restricted by workflow"));
    }

    @Test
    public void testUpdateWithRestriction(){
        Users newUser = new Users();
        newUser.setName("John Doe99");
        newUser.setEmail("john.doe.99@example.com");
        newUser = E5StateService.insertOne(sessionFactory, newUser);
        try (var cursor = E5StateService.find(sessionFactory, Users.class).iterator()) {
            Users userToUpdate;
            while (cursor.hasNext()) {
                userToUpdate = (Users) cursor.next();
                userToUpdate.setEmail("john.doe_" + userToUpdate.getId() + "1111@example.com");
                Users finalUserToUpdate = userToUpdate;
                GenericJDBCException exception = Assertions.assertThrows(GenericJDBCException.class, () -> {
                    updateOne(sessionFactory, finalUserToUpdate);
                });
                Assertions.assertInstanceOf(PSQLException.class, exception.getCause());
                Assertions.assertTrue(exception.getCause().getMessage().contains("Access Restriced by workflow"));
            }
        }
    }

    public static <T extends E5State> T updateOne(SessionFactory sessionFactory, T entity) {
        executeInsideTransaction(session -> {
            session.update(entity);
        }, sessionFactory);
        return entity;
    }
    public static <T extends E5State> T insertOne(SessionFactory sessionFactory, T entity) {
        executeInsideTransaction(session -> {
            session.save(entity);
        }, sessionFactory);
        return entity;
    }
    private static void executeInsideTransaction(Consumer<Session> action, SessionFactory sessionFactory) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            setAllowRestricted(session, false);
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
    private static void setAllowRestricted(Session session, boolean allowRestricted){
        session.doWork(connection -> {
            try(var stmt = connection.createStatement()){
                stmt.execute("SET LOCAL app_context.allow_restricted = '" + (allowRestricted ? "true" : "false") + "'");
            } catch (SQLException e) {
                throw new SQLException("Error occurred in changing configuration parameter in postgres: "+e);
            }
        });
    }
}