package e5.stateservice.service;

import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class E5StateFilterOptions<T extends E5State> {
    private final List<E5StateFilterCriterion> criteria = new ArrayList<>();
    private final List<E5StateFilterGroup<T>> groups = new ArrayList<>();
    private Class<T> entityClass;

    private E5StateFilterOptions(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * Create E5StateFilterOptions instance
     * @param entityClass - class to which filters has to be created
     * @return new E5StateFilterOptions instance for the entityClass Object
     * @param <T>
     */
    public static <T extends E5State> E5StateFilterOptions<T> create(Class<T> entityClass) {
        return new E5StateFilterOptions<T>(entityClass);
    }

    /**
     * Adding equal Filter to the field and value
     * @param field - to which equal filter has to be applied
     * @param value - to which filter has to be equated
     * @return E5StateFilterOptions instance for the entityClass Object with filter added
     * @param <F>
     */
    public <F> E5StateFilterOptions<T> eq(E5SearchField<T, F> field, F value) {
        criteria.add(new E5StateFilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " = "));
        return this;
    }

    /**
     * Adding Lessthan Filter to the field and value
     * @param field - to which lessthan filter has to be applied
     * @param value - to which filter has to be equated
     * @return E5StateFilterOptions instance for the entityClass Object with filter added
     * @param <F>
     */
    public<F> E5StateFilterOptions<T> lt(E5SearchField<T, F> field, F value) {
        criteria.add(new E5StateFilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " < "));
        return this;
    }

    /**
     * Adding greaterthan Filter to the field and value
     * @param field - to which greaterthan filter has to be applied
     * @param value - to which filter has to be equated
     * @return E5StateFilterOptions instance for the entityClass Object with filter added
     * @param <F>
     */
    public <F> E5StateFilterOptions<T> gt(E5SearchField<T, F> field, F value) {
        criteria.add(new E5StateFilterCriterion(field.getName().toLowerCase(Locale.ROOT), value, " > "));
        return this;
    }

    /**
     * Adding FilterGroup to the Filter already added
     * @param group - which has to added to the filter
     * @return E5StateFilterOptions instance for the entityClass Object with filtergroup added
     */
    public E5StateFilterOptions<T> addGroup(E5StateFilterGroup<T> group) {
        groups.add(group);
        return this;
    }

    public String toHql() {
        StringBuilder hql = new StringBuilder();
        hql.append(" (");
        if (!criteria.isEmpty()) {
            //hql.append(" WHERE ");
            for (E5StateFilterCriterion criterion : criteria) {
                String modifiedFieldName = criterion.getField() + (int)(Math.random()*100);
                criterion.setFieldParamName(modifiedFieldName);
                hql.append(criterion.getField()).append(criterion.getOperator()).append(":").append(criterion.getFieldParamName()).append(" AND ");
            }
            if (groups.size() == 0) {
                hql.delete(hql.length() - 5, hql.length());
            }// Remove last " AND "
        }
        for (E5StateFilterGroup<T> group : groups) {
            hql.append(" (");
            /*if (criteria.isEmpty()) {
                hql.append(" WHERE ");
                criteria.add(new FilterCriterion("1", 1, " = ")); // Adding a dummy criterion to initiate the where clause
            } else {
                hql.append(group.getOperator() == E5FilterGroup.LogicalOperator.AND ? " AND (" : " OR (");
            }*/

            for (E5StateFilterOptions<T> filter : group.getFilters()) {
                hql.append(filter.toHql()).append(" ").append(group.getOperator()).append(" ");
            }
            hql.delete(hql.length() - (" " + group.getOperator() + " ").length(), hql.length()); // Remove last " AND "
            hql.append(") ");
        }
        hql.append(") ");
        return hql.toString().trim();
    }

    public void setParameters(Query<T> query) {
        for (E5StateFilterCriterion criterion : criteria) {
            query.setParameter(criterion.getFieldParamName(), criterion.getValue());
        }

        for (E5StateFilterGroup<T> group : groups) {
            for (E5StateFilterOptions<T> filter : group.getFilters()) {
                filter.setParameters(query);
            }
        }
    }

    private static class E5StateFilterCriterion {
        private final String field;
        private final Object value;
        private final String operator;
        private String fieldParamName;

        public E5StateFilterCriterion(String field, Object value, String operator) {
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
