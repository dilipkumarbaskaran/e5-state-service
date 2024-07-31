package e5.stateservice.examples;


import e5.stateservice.examples.model.Users;
import e5.stateservice.examples.model.UsersField;
import e5.stateservice.service.E5FilterOptions;
import e5.stateservice.service.E5FilterGroup;
import e5.stateservice.service.E5StateService;

public final class Main {
    public static void main(String[] args) {

        // Insert a new user
        Users newUser = new Users();
        newUser.setName("John Doe");
        newUser.setEmail("john.doe@example.com");
        E5StateService.insertOne(newUser);

        // Fetch users with filters, sorting, limit, and skip

        // Fetch users with filters, sorting, limit, and skip
        E5FilterOptions<Users, UsersField> E5FilterOptions1 = new E5FilterOptions<Users, UsersField>()
                .eq(UsersField.NAME, "John Doe")
                .gt(UsersField.ID, 1);

        E5FilterOptions<Users, UsersField> E5FilterOptions2 = new E5FilterOptions<Users, UsersField>()
                .lt(UsersField.ID, 100);

        E5FilterGroup<Users, UsersField> filterGroup = new E5FilterGroup<Users, UsersField>(E5FilterGroup.LogicalOperator.OR)
                .addFilter(E5FilterOptions1)
                .addFilter(E5FilterOptions2);

        E5FilterOptions<Users, UsersField> combinedE5FilterOptions = new E5FilterOptions<Users, UsersField>()
                .lt(UsersField.ID, 1000)
                .addGroup(filterGroup);

        try (var cursor = E5StateService.find(Users.class, UsersField.class)
                .filter(combinedE5FilterOptions)
                .sort(UsersField.NAME, true)
                .limit(10)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = (Users) cursor.next();
                System.out.println("Users -- " + user.toString());
            }
        }

        /*// Update a user
        try (var cursor = E5StateService.find(Users.class).filter(combinedE5FilterOptions).iterator()) {
            if (cursor.hasNext()) {
                Users userToUpdate = (Users) cursor.next();
                userToUpdate.setEmail("john.doe_updated@example.com");
                E5StateService.updateOne(userToUpdate);
            }
        }

        try (var cursor = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort("name", true)
                .limit(10)
                .skip(0)
                .batchSize(5)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = (Users) cursor.next();
                System.out.println("Users -- " + user.toString());
            }
        }

        // Delete a user
        try (var cursor = E5StateService.find(Users.class).filter(combinedE5FilterOptions).iterator()) {
            if (cursor.hasNext()) {
                E5StateService.deleteOne(Users.class, ((Users)cursor.next()).getId());
            }
        }

        try (var cursor = E5StateService.find(Users.class)
                .filter(combinedE5FilterOptions)
                .sort("name", true)
                .limit(10)
                .skip(0)
                .batchSize(5)
                .iterator()) {

            while (cursor.hasNext()) {
                Users user = (Users) cursor.next();
                System.out.println("User -- " + user.toString());
            }
        }*/
    }
}

