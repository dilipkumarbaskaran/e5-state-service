package e5.stateservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class E5DBServiceProperties {
    String endpoint;
    String dbName;
    String schemaName;
    String dbUserName;
    String dbPassword;
}
