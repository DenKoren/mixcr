package com.milaboratory.mixcr.postanalysis;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.IteratorOutputPortAdapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 *
 */
public class TestDataset<T> implements Dataset<T>, Iterable<T> {
    public final List<T> data;
    public final String id;

    public TestDataset(List<T> data) {
        this.data = data;
        this.id = UUID.randomUUID().toString();
    }

    public TestDataset(Dataset<T> data) {
        this.data = new ArrayList<>();
        try (OutputPortCloseable<T> port = data.mkElementsPort()) {
            for (T t : CUtils.it(port)) {
                this.data.add(t);
            }
        }
        this.id = data.id();
    }

    @Override
    public Iterator<T> iterator() {
        return data.iterator();
    }

    public Stream<T> stream() {
        return data.stream();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public OutputPortCloseable<T> mkElementsPort() {
        final IteratorOutputPortAdapter<T> adapter = new IteratorOutputPortAdapter<>(data);
        return new OutputPortCloseable<T>() {
            @Override
            public void close() { }

            @Override
            public T take() {
                return adapter.take();
            }
        };
    }
}
