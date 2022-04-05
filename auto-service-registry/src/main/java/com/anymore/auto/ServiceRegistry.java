package com.anymore.auto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * Created by anymore on 2022/3/31.
 */
@SuppressWarnings("unchecked")
class ServiceRegistry {

    private static final Map<Class<?>, List<Callable<?>>> serviceCreators = new HashMap<>();


    private static <S> void register(Class<S> clazz, Callable<S> creator) {
        serviceCreators.computeIfAbsent(clazz, new Function<Class<?>, List<Callable<?>>>() {
            @Override
            public List<Callable<?>> apply(Class<?> aClass) {
                return new LinkedList<>();
            }
        }).add(creator);
    }

    static <S> List<S> get(Class<S> clazz) {
        final List<Callable<?>> creators = serviceCreators.getOrDefault(clazz, new ArrayList<Callable<?>>());
        final List<S> services = new ArrayList<>(creators.size());
        if (!creators.isEmpty()) {
            for (Callable<?> creator : creators) {
                try {
                    services.add((S) creator.call());
                } catch (Exception e) {
                    throw new ServiceConfigurationError(String.format("create class %s error!", clazz.getCanonicalName()), e);
                }
            }
        }
        return Collections.unmodifiableList(services);
    }
}
