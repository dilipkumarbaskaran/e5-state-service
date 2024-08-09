package e5.stateservice.model.state.field;

import e5.stateservice.model.E5FieldEnum;

public enum UsersField implements E5FieldEnum {
    ID("id"),
    NAME("name"),
    EMAIL("email");

    private final String fieldName;

    UsersField(String fieldName) {
        this.fieldName = fieldName;
    }
    public String getFieldName() {
        return fieldName;
    }
}
