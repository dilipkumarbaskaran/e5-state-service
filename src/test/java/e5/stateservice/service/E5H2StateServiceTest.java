package e5.stateservice.service;

import e5.stateservice.model.E5StateServiceProperties;
import e5.stateservice.model.state.Users;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class E5H2StateServiceTest {

    @BeforeEach
    public void setupStateService() {

        E5StateServiceProperties stateServiceProps = E5StateServiceProperties.builder()
                .dbName("yourdb")
                .schemaName("schema1")
                .build();
        E5StateServiceInitializer.init(stateServiceProps, false);
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

        int countPrevious = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();
        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe1@example.com");
        newUser = E5StateService.insertOne(newUser);

        Assertions.assertTrue(newUser.getId()!=0);

        int countAfter = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();

        Assertions.assertEquals(countPrevious+1, countAfter);

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
        int countPrevious = E5StateService.find(Users.class)
                .list().size();
        E5StateService.insertMany(List.of(newUser, newUser1));
        int countAfter = E5StateService.find(Users.class)
                .list().size();
        Assertions.assertEquals(countPrevious+2, countAfter);
    }

    @Test
    public void testInsertManyWithException() {

        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe4@example.com");
        Assertions.assertThrows(ConstraintViolationException.class, ()-> {E5StateService.insertMany(List.of(newUser, newUser));});
    }

    @Test
    public void testUpdate() {
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe5@example.com");
        newUser = E5StateService.insertOne(newUser);
        // Update a user
        try (var cursor = E5StateService.find(Users.class).iterator()) {
            Users userToUpdate;
            while (cursor.hasNext()) {
                userToUpdate = (Users) cursor.next();
                userToUpdate.setEmail("john.doe_" + userToUpdate.getId() + "1111@example.com");
                E5StateService.updateOne(userToUpdate);
            }
        }

        try (var cursor = E5StateService.find(Users.class)
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
        int countPrevious = E5StateService.find(Users.class)
                .list().size();
        E5StateService.insertMany(List.of(newUser, newUser1));
        // Update Multiple user
        List<Users> users = E5StateService.find(Users.class)
                .list();
        for (Users user :users) {
            user.setEmail("john.doe_" + user.getId() + "1111@example.com");
        }

        E5StateService.updateMany(users);

        try (var cursor = E5StateService.find(Users.class)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = cursor.next();
                Assertions.assertEquals("john.doe_" + user.getId() + "1111@example.com", user.getEmail());
            }
        }
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

        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe1@example.com");
        newUser = E5StateService.insertOne(newUser);

        int countAfter = E5StateService.find(Users.class)
                .filter(filterOptions1)
                .list().size();
        Assertions.assertEquals(1, countAfter);
    }

    @Test
    public void testUpdateManyWithException() {

        // Update Multiple user
        List<Users> users = E5StateService.find(Users.class)
                .list();
        for (Users user :users) {
            user.setEmail("john.doe_1111@example.com");
        }

        Assertions.assertThrows(ConstraintViolationException.class, ()-> {E5StateService.updateMany(users);});
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

        int countBefore = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();

        if (countBefore>0) {
            // Delete a user
            try (var cursor = E5StateService.find(Users.class)
                    .filter(combinedE5FilterOptions)
                    .iterator()) {
                if (cursor.hasNext()) {
                    E5StateService.deleteOne(Users.class, ((Users) cursor.next()).getId());
                }
            }

            int countAfter = E5StateService.find(Users.class)
                    .filter(combinedE5FilterOptions)
                    .sort(Users.NAME, true)
                    .list().size();

            Assertions.assertEquals(countBefore - 1, countAfter);
        }

    }
}
