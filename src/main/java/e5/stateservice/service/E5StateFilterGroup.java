package e5.stateservice.service;

import e5.stateservice.model.E5State;
import lombok.extern.java.Log;

import java.util.ArrayList;
import java.util.List;

public final class E5StateFilterGroup<T extends E5State> {
    public enum LogicalOperator {
        AND, OR
    }

    private final LogicalOperator operator;
    private final List<E5StateFilterOptions<T>> filters;
    private Class<T> entityCLass;


    private E5StateFilterGroup(Class<T> entityCLass, LogicalOperator operator) {
        this.entityCLass = entityCLass;
        this.operator = operator;
        this.filters = new ArrayList<>();
    }

    /**
     * Creating E5StateFilterGroup instance for the entityClass with operator mentioned
     * @param entityClass - to which group has to be created
     * @param operator - will be between every filter added in the group
     * @return new E5StateFilterGroup instance
     * @param <T>
     */
    public static <T extends E5State> E5StateFilterGroup<T> create(Class<T> entityClass, LogicalOperator operator) {
        return new E5StateFilterGroup<T>(entityClass, operator);
    }

    /**
     * Adding filter to the filter group
     * @param filter - filter to be grouped in this group
     * @return E5StateFilterGroup instance after adding the filter to the group
     */
    public E5StateFilterGroup<T> addFilter(E5StateFilterOptions<T> filter) {
        filters.add(filter.clone());
        return this;
    }

    public E5StateFilterGroup<T> clone(){
        E5StateFilterGroup<T> copyObject = new E5StateFilterGroup(entityCLass, operator);
        for (E5StateFilterOptions<T> filter : filters) {
            copyObject.filters.add(filter.clone());
        }
        return copyObject;
    }

    public LogicalOperator getOperator() {
        return operator;
    }

    public List<E5StateFilterOptions<T>> getFilters() {
        return filters;
    }
}
