package e5.stateservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class E5State<T extends E5State<T>> implements  Cloneable {
    @CreationTimestamp
    @Column(name = "created_date_time", updatable = false)
    protected Instant createdDateTime;

    @Transient
    private final static E5SearchField<? extends E5State<?>, Instant> CREATED_AT = new E5SearchField<E5State<?>,Instant>() {


        public String getName() {
            return "createdDateTime";
        }

    };

    // Protected static method to access the ID field in a type-safe way
    @SuppressWarnings("unchecked")
    protected static <T extends E5State<T>> E5SearchField<T, Instant> createdAtField() {
        return (E5SearchField<T, Instant>) CREATED_AT;
    }

    @UpdateTimestamp
    @Column(name = "updated_date_time")
    protected Instant updatedDateTime;

    @Transient
    private final static E5SearchField<? extends E5State<?>, Instant> UPDATED_AT = new E5SearchField<E5State<?>,Instant>() {


        public String getName() {
            return "updatedDateTime";
        }

    };

    // Protected static method to access the ID field in a type-safe way
    @SuppressWarnings("unchecked")
    protected static <T extends E5State<T>> E5SearchField<T, Instant> updatedAtField() {
        return (E5SearchField<T, Instant>) UPDATED_AT;
    }

    @Column(name = "created_by", updatable = false)
    protected String createdBy;

    @Column(name = "updated_by")
    protected String updatedBy;

    @Override
    public E5State clone() throws CloneNotSupportedException {
        E5State e5State = (E5State) super.clone();
        return e5State;
    }
}
