package e5.stateservice.service;

import e5.stateservice.model.E5SearchField;
import e5.stateservice.model.E5State;
import jakarta.persistence.LockModeType;
import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class E5StateIterable<T extends E5State> {
    @Getter
    private final Class<T> entityClass;
    private final SessionFactory sessionFactory;
    private E5StateFilterOptions<T> filterOptions;
    private List<String> sortFieldList = new ArrayList<>();
    private List<Boolean> ascendingList = new ArrayList<>();
    private int limit = -1;
    private int skip = 0;
    private int batchSize = 50; // not used in initial release
    private static final Logger logger = LoggerFactory.getLogger(E5StateIterable.class);

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
        this.sortFieldList.add(field.getName());
        this.ascendingList.add(ascending);
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
        Transaction iteratorTransaction = session.beginTransaction();
        E5StateCursor iteratorStateCursor = new E5StateCursor<>(entityClass, session, this.createQuery(session), batchSize);
        iteratorTransaction.commit();
        return iteratorStateCursor;
    }

    /**
     * Get all the records with operations performed
     * @return list of output records
     */
    public List<T> list() {
        Session session = sessionFactory.openSession();
        Transaction listingTransaction = session.beginTransaction();
        E5StateCursor<T> e5StateCursor = new E5StateCursor<>(entityClass, session, this.createQuery(session), batchSize);
        List<T> recordList = e5StateCursor.list();
        listingTransaction.commit();
        e5StateCursor.close();
        return recordList;
    }

    public Optional<T> fetchAndUpdate(EntityUpdater<T> updater) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();

            limit(1);// Ensure only one row is fetched
            Query query = this.createQuery(session);
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE); // Lock the row

            Optional<T> result = query.uniqueResultOptional();

            if (result.isPresent()) {
                T entity = result.get();
                updater.update(entity);  // Apply the update function
                session.update(entity); // Update entity
            }

            transaction.commit();
            return result;
        } catch (Exception e) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            throw new RuntimeException("Error in atomic fetch and update", e);
        }
    }


    private Query createQuery(Session session) {
        StringBuilder hql = new StringBuilder("FROM " + entityClass.getName());
        // Initialize a counter to differentiate field values
        AtomicInteger counter = new AtomicInteger(0);
        if (filterOptions != null) {
            hql.append(" WHERE ").append(filterOptions.toHql(counter));
        }

        if (sortFieldList != null  && !sortFieldList.isEmpty()){
            hql.append(" ORDER BY ");
            for (int i=0;i< sortFieldList.size();i++) {
                hql.append(sortFieldList.get(i)).append(ascendingList.get(i).equals(Boolean.TRUE) ? " ASC, " : " DESC, ");
            }
            hql.delete(hql.length()-2, hql.length());
        }

        logger.debug("Constructed HQL Query String: " + hql.toString());
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
