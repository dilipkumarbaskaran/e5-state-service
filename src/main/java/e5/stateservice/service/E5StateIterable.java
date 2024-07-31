package e5.stateservice.service;

import e5.stateservice.examples.model.UsersField;
import e5.stateservice.model.E5FieldEnum;
import e5.stateservice.model.E5State;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import java.util.List;

public final class E5StateIterable<T extends E5State, F extends Enum<F> & E5FieldEnum> {
    private final Class<T> entityClass;
    private final SessionFactory sessionFactory;
    private E5FilterOptions<T, F> filterOptions;
    private String sortField;
    private boolean ascending = true;
    private int limit = -1;
    private int skip = 0;
    private int batchSize = 50;

    public E5StateIterable(Class<T> entityClass, SessionFactory sessionFactory) {
        this.entityClass = entityClass;
        this.sessionFactory = sessionFactory;
    }

    public E5StateIterable<T, F> filter(E5FilterOptions<T, F> filterOptions) {
        this.filterOptions = filterOptions;
        return this;
    }

    public E5StateIterable<T, F> sort(F field, boolean ascending) {
        this.sortField = field.getFieldName();
        this.ascending = ascending;
        return this;
    }

    public E5StateIterable<T, F> limit(int limit) {
        this.limit = limit;
        return this;
    }

    public E5StateIterable<T, F> skip(int skip) {
        this.skip = skip;
        return this;
    }

    public E5StateIterable<T, F> batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    public E5StateCursor<T> iterator() {
        Session session = sessionFactory.openSession();
        StringBuilder hql = new StringBuilder("FROM " + entityClass.getName());

        if (filterOptions != null) {
            hql.append(" WHERE ").append(filterOptions.toHql());
        }

        if (sortField != null) {
            hql.append(" ORDER BY ").append(sortField).append(ascending ? " ASC" : " DESC");
        }

        Query<T> query = session.createQuery(hql.toString(), entityClass);
        if (filterOptions != null) {
            filterOptions.setParameters(query);
        }

        if (limit > 0) {
            query.setMaxResults(limit);
        }

        if (skip > 0) {
            query.setFirstResult(skip);
        }

        return new E5StateCursor<>(entityClass, session, query, batchSize);
    }
}
