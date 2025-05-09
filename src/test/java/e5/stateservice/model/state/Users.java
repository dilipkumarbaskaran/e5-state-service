package e5.stateservice.model.state;


import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_name", columnList = "name")
})
public class Users extends E5State {

    @Id
    @Column(nullable = false, updatable = false, name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Transient
    public final static E5SearchField<Users, Long> ID = new E5SearchField<Users,Long>() {
        public String getName() {
            return "id";
        }
    };

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "name", insertable = false, updatable = false)),
            @AttributeOverride(name = "email", column = @Column(name = "email", insertable = false, updatable = false))
    })
    private NameEmailFilter nameEmail;

    @Transient
    public final static E5SearchField<Users, NameEmailFilter> NAMEEMAIL = new E5SearchField<Users,NameEmailFilter>() {
        public String getName() {
            return "nameEmail";
        }
    };

    @Column(name = "email", unique = true, length = 255)
    public String email;
    @Lob
    @Column(name = "description", length = 255)
    public String description;
    @Transient
    public final static E5SearchField<Users, String> EMAIL = new E5SearchField<Users,String>() {


        public String getName() {
            return "email";
        }

    }
            ;
    /**
     *
     * (Required)
     *
     */
    @Column(name = "name", length = 255, nullable = false)
    public String name;
    @Transient
    public final static E5SearchField<Users, String> NAME = new E5SearchField<Users,String>() {


        public String getName() {
            return "name";
        }

    }
            ;

}
