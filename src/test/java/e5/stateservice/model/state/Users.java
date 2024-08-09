package e5.stateservice.model.state;


import e5.stateservice.model.E5State;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name="Users", indexes = {
        @Index(name = "idx_user_name", columnList = "name")
})
public class Users implements E5State {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String name;
    private String email;

    // Getters and Setters
}
