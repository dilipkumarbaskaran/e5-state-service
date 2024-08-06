package e5.stateservice.model.state;

import e5.stateservice.model.E5FieldEnum;

public enum UsersField implements E5FieldEnum {
    ID("id"),
    NAME("name"),
    EMAIL("email");

    private final String fieldName;

    UsersField(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }
}
