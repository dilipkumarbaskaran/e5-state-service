package e5.stateservice.model.state;


import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name="Users", indexes = {
        @Index(name = "idx_user_name", columnList = "name"),
        @Index(name = "idx_user_email", columnList = "email")
})
public class Users implements E5State {

    @Id
    @Column(nullable = false, name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public int id;
    @Transient
    public final static E5SearchField<Users, Integer> ID = new E5SearchField<Users,Integer>() {


        public String getName() {
            return "id";
        }

    }
            ;
    @Column(name = "email")
    public String email;
    @Transient
    public final static E5SearchField<Users, String> EMAIL = new E5SearchField<Users,String>() {


        public String getName() {
            return "email";
        }

    };
    /**
     *
     * (Required)
     *
     */
    @Column(name = "name", unique = true, length = 255, nullable = false)
    public String name;
    @Transient
    public final static E5SearchField<Users, String> NAME = new E5SearchField<Users,String>() {


        public String getName() {
            return "name";
        }

    }
            ;

}
