package e5.stateservice.service;

import e5.stateservice.model.E5State;

import java.util.ArrayList;
import java.util.List;

public final class E5FilterGroup<T extends E5State> {
    public enum LogicalOperator {
        AND, OR
    }

    private final LogicalOperator operator;
    private final List<E5FilterOptions<T>> filters;

    public E5FilterGroup(LogicalOperator operator) {
        this.operator = operator;
        this.filters = new ArrayList<>();
    }

    public E5FilterGroup<T> addFilter(E5FilterOptions<T> filter) {
        filters.add(filter);
        return this;
    }

    public LogicalOperator getOperator() {
        return operator;
    }

    public List<E5FilterOptions<T>> getFilters() {
        return filters;
    }
}
