package e5.stateservice.model.state;

import e5.stateservice.model.E5State;
import jakarta.persistence.*;

@Entity
@Table(name = "triggertable")
public class TriggerTest extends E5State {

    @Id
    @Column(nullable = false, updatable = false, name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public String id;

    @Column(name = "email", unique = true, length = 255)
    public String email;

    @Column(name = "description", length = 255)
    public String description;

}
