package e5.stateservice.service;

import e5.stateservice.model.E5StateServiceProperties;
import e5.stateservice.model.state.Users;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class E5StateServiceTest {

    @BeforeEach
    public void setupStateService() {
        E5StateServiceProperties stateServiceProps = E5StateServiceProperties.builder()
                .endpoint("localhost:5432")
                .dbName("yourdb")
                .schemaName("schema1")
                .dbUserName("postgres")
                .dbPassword("pgadmin").build();
        E5StateServiceInitializer.init(stateServiceProps);
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
        newUser.setEmail("john.doe@example.com");
        newUser = E5StateService.insertOne(newUser);

        Assertions.assertTrue(newUser.getId()!=0);

        int countAfter = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort(Users.NAME, true)
                .list().size();

        Assertions.assertEquals(countPrevious+1, countAfter);

    }

    @Test
    public void testUpdate() {
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
        // Update a user
        try (var cursor = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions).iterator()) {
            Users userToUpdate;
            while (cursor.hasNext()) {
                userToUpdate = (Users) cursor.next();
                userToUpdate.setEmail("john.doe_" + userToUpdate.getId() + "1111@example.com");
                E5StateService.updateOne(userToUpdate);
            }
        }

        try (var cursor = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = cursor.next();
                Assertions.assertEquals("john.doe_" + user.getId() + "1111@example.com", user.getEmail());
            }
        }
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
