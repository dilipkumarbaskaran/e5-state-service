package e5.stateservice.service;

import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Locale;

public final class E5StateIterable<T extends E5State> {
    @Getter
    private final Class<T> entityClass;
    private final SessionFactory sessionFactory;
    private E5StateFilterOptions<T> filterOptions;
    private String sortField;
    private boolean ascending = true;
    private int limit = -1;
    private int skip = 0;
    private int batchSize = 50; // not used in initial release

    public E5StateIterable(Class<T> entityClass, SessionFactory sessionFactory) {
        this.entityClass = entityClass;
        this.sessionFactory = sessionFactory;
    }

    /**
     * Filter records with filteroptions
     * @param filterOptions records to be filtered with
     * @return E5StateIterable Object to add further operations or fetch record
     */
    public E5StateIterable<T> filter(E5StateFilterOptions<T> filterOptions) {
        this.filterOptions = filterOptions;
        return this;
    }

    /**
     * Sort records
     * @param field to which sorting has to be done
     * @param ascending boolean value to say its ascending/descending
     * @return E5StateIterable Object to add further operations or fetch record
     * @param <F>
     */
    public <F> E5StateIterable<T> sort(E5SearchField<T,F> field, boolean ascending) {
        this.sortField = field.getName().toLowerCase(Locale.ROOT);
        this.ascending = ascending;
        return this;
    }

    /**
     * Limit Records to be fetched
     * @param limit - no of records to be fetched
     * @return E5StateIterable Object to add further operations or fetch record
     */
    public E5StateIterable<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Skipping records at the Start
     * @param skip no of records to be skipped at the start
     * @return E5StateIterable Object to add further operations or fetch record
     */
    public E5StateIterable<T> skip(int skip) {
        this.skip = skip;
        return this;
    }

    /**
     * Limiting no of records to be fetched at the given instance
     * @param batchSize - no of records to be fetched at this instance
     * @return E5StateIterable Object to add further operations or fetch record
     */
    public E5StateIterable<T> batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    /**
     * create query and fetch records with the operations mentioned
     * @return E5StateCursor Object to iterate over records
     */
    public E5StateCursor<T> iterator() {
        Session session = sessionFactory.openSession();
        return new E5StateCursor<>(entityClass, session, this.createQuery(session), batchSize);
    }

    /**
     * Get all the records with operations performed
     * @return list of output records
     */
    public List<T> list() {
        Session session = sessionFactory.openSession();
        E5StateCursor<T> e5StateCursor = new E5StateCursor<>(entityClass, session, this.createQuery(sessionFactory.openSession()), batchSize);
        List<T> recordList = e5StateCursor.list();
        e5StateCursor.close();
        return recordList;
    }


    private Query createQuery(Session session) {
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

        return query;
    }
}
