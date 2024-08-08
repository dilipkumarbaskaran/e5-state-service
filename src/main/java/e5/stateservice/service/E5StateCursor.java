package e5.stateservice.service;


import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

@NoArgsConstructor
@AllArgsConstructor
public final class E5StateCursor<T> implements Iterator<T>, AutoCloseable {
    private Class<T> entityClass;
    private Session session;
    private Query<T> query;
    private int batchSize;
    private int currentIndex;
    private List<T> currentBatch;

    public E5StateCursor(Class<T> entityClass, Session session, Query<T> query, int batchSize) {
        this.entityClass = entityClass;
        this.session = session;
        this.query = query;
        this.batchSize = batchSize; // not used in initial release
        this.currentIndex = 0;
        fetchNextBatch();
    }

    private void fetchNextBatch() {
        query.setFirstResult(currentIndex);
        //query.setMaxResults(batchSize);
        System.out.println("query " + query.getQueryString());
        currentBatch = query.list();
        currentIndex += currentBatch.size();
    }

    public List<T> list() {
        return currentBatch;
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

