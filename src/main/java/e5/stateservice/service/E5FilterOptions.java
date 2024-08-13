package e5.stateservice.service;

import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class E5FilterOptions<T extends E5State> {
    private final List<E5FilterCriterion> criteria = new ArrayList<>();
    private final List<E5FilterGroup<T>> groups = new ArrayList<>();

    public <F> E5FilterOptions<T> eq(E5SearchField<T, F> field, F value) {
        criteria.add(new E5FilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " = "));
        return this;
    }

    public<F> E5FilterOptions<T> lt(E5SearchField<T, F> field, F value) {
        criteria.add(new E5FilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " < "));
        return this;
    }

    public <F> E5FilterOptions<T> gt(E5SearchField<T, F> field, F value) {
        criteria.add(new E5FilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " > "));
        return this;
    }

    public E5FilterOptions<T> addGroup(E5FilterGroup<T> group) {
        groups.add(group);
        return this;
    }

    public String toHql() {
        StringBuilder hql = new StringBuilder();
        hql.append(" (");
        if (!criteria.isEmpty()) {
            //hql.append(" WHERE ");
            for (E5FilterCriterion criterion : criteria) {
                String modifiedFieldName = criterion.getField() + (int)(Math.random()*100);
                criterion.setFieldParamName(modifiedFieldName);
                hql.append(criterion.getField()).append(criterion.getOperator()).append(":").append(criterion.getFieldParamName()).append(" AND ");
            }
            if (groups.size() == 0) {
                hql.delete(hql.length() - 5, hql.length());
            }// Remove last " AND "
        }
        for (E5FilterGroup<T> group : groups) {
            hql.append(" (");
            /*if (criteria.isEmpty()) {
                hql.append(" WHERE ");
                criteria.add(new FilterCriterion("1", 1, " = ")); // Adding a dummy criterion to initiate the where clause
            } else {
                hql.append(group.getOperator() == E5FilterGroup.LogicalOperator.AND ? " AND (" : " OR (");
            }*/

            for (E5FilterOptions<T> filter : group.getFilters()) {
                hql.append(filter.toHql()).append(" ").append(group.getOperator()).append(" ");
            }
            hql.delete(hql.length() - (" " + group.getOperator() + " ").length(), hql.length()); // Remove last " AND "
            hql.append(") ");
        }
        hql.append(") ");
        return hql.toString().trim();
    }

    public void setParameters(Query<T> query) {
        for (E5FilterCriterion criterion : criteria) {
            query.setParameter(criterion.getFieldParamName(), criterion.getValue());
        }

        for (E5FilterGroup<T> group : groups) {
            for (E5FilterOptions<T> filter : group.getFilters()) {
                filter.setParameters(query);
            }
        }
    }

    private static class E5FilterCriterion {
        private final String field;
        private final Object value;
        private final String operator;
        private String fieldParamName;

        public E5FilterCriterion(String field, Object value, String operator) {
            this.field = field;
            this.value = value;
            this.operator = operator;
        }

        public String getField() {
            return field;
        }

        public Object getValue() {
            return value;
        }

        public String getOperator() {
            return operator;
        }

        public void setFieldParamName(String modifiedFieldName) {
            this.fieldParamName = modifiedFieldName;
        }

        public String getFieldParamName() {
            return fieldParamName;
        }
    }
}
