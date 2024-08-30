package e5.stateservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class E5State<T extends E5State<T>> implements  Cloneable {
    @Column(name = "created_date_time", updatable = false)
    protected LocalDateTime createdDateTime;

    @Transient
    private final static E5SearchField<? extends E5State<?>, LocalDateTime> CREATED_AT = new E5SearchField<E5State<?>,LocalDateTime>() {


        public String getName() {
            return "created_datetime";
        }

    };

    // Protected static method to access the ID field in a type-safe way
    @SuppressWarnings("unchecked")
    protected static <T extends E5State<T>> E5SearchField<T, LocalDateTime> createdAtField() {
        return (E5SearchField<T, LocalDateTime>) CREATED_AT;
    }

    @Column(name = "updated_date_time")
    protected LocalDateTime updatedDateTime;

    @Transient
    private final static E5SearchField<? extends E5State<?>, LocalDateTime> UPDATED_AT = new E5SearchField<E5State<?>,LocalDateTime>() {


        public String getName() {
            return "updated_date_time";
        }

    };

    // Protected static method to access the ID field in a type-safe way
    @SuppressWarnings("unchecked")
    protected static <T extends E5State<T>> E5SearchField<T, LocalDateTime> updatedAtField() {
        return (E5SearchField<T, LocalDateTime>) UPDATED_AT;
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
