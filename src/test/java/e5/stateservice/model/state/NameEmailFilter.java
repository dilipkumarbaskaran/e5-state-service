package e5.stateservice.model.state;

import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class NameEmailFilter implements Serializable {

    private String name;
    private String email;

    public NameEmailFilter() {}

    public NameEmailFilter(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
