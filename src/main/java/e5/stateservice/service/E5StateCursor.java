package e5.stateservice.service;


import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class E5StateCursor<T> implements Iterator<T>, AutoCloseable {
    private final Class<T> entityClass;
    private final Session session;
    private final Query<T> query;
    private final int batchSize;
    private int currentIndex;
    private List<T> currentBatch;

    public E5StateCursor(Class<T> entityClass, Session session, Query<T> query, int batchSize) {
        this.entityClass = entityClass;
        this.session = session;
        this.query = query;
        this.batchSize = batchSize;
        this.currentIndex = 0;
        fetchNextBatch();
    }

    private void fetchNextBatch() {
        query.setFirstResult(currentIndex);
        query.setMaxResults(batchSize);
        System.out.println("query " + query.getQueryString());
        currentBatch = query.list();
        currentIndex += currentBatch.size();
    }

    @Override
    public boolean hasNext() {
        if (currentBatch.isEmpty()) {
            return false;
        }
        if (currentBatch.size() < batchSize) {
            return !currentBatch.isEmpty();
        }
        return true;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T nextElement = currentBatch.remove(0);
        if (currentBatch.isEmpty() && currentIndex >= batchSize) {
            fetchNextBatch();
        }
        return nextElement;
    }

    @Override
    public void close() {
        session.close();
    }
}

