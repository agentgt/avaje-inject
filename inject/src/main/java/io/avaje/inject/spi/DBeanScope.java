package io.avaje.inject.spi;

import io.avaje.applog.AppLog;
import io.avaje.inject.BeanEntry;
import io.avaje.inject.BeanScope;
import io.avaje.inject.Priority;
import io.avaje.lang.NonNullApi;
import io.avaje.lang.Nullable;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@NonNullApi
final class DBeanScope implements BeanScope {

  private static final System.Logger log = AppLog.getLogger("io.avaje.inject");

  private final ReentrantLock lock = new ReentrantLock();
  private final List<Runnable> postConstruct;
  private final List<AutoCloseable> preDestroy;
  private final DBeanMap beans;
  private final ShutdownHook shutdownHook;
  private final BeanScope parent;
  private boolean shutdown;
  private boolean closed;

  DBeanScope(boolean withShutdownHook, List<AutoCloseable> preDestroy, List<Runnable> postConstruct, DBeanMap beans, BeanScope parent) {
    this.preDestroy = preDestroy;
    this.postConstruct = postConstruct;
    this.beans = beans;
    this.parent = parent;
    if (withShutdownHook) {
      this.shutdownHook = new ShutdownHook(this);
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    } else {
      this.shutdownHook = null;
    }
  }

  @Override
  public String toString() {
    return "BeanScope{" + beans + '}';
  }

  @Override
  public List<BeanEntry> all() {
    IdentityHashMap<DContextEntryBean, DEntry> map = new IdentityHashMap<>();
    if (parent != null) {
      ((DBeanScope) parent).addAll(map);
    }
    addAll(map);
    return new ArrayList<>(map.values());
  }

  void addAll(Map<DContextEntryBean, DEntry> map) {
    beans.addAll(map);
  }

  @Override
  public boolean contains(String type) {
    return beans.contains(type);
  }

  @Override
  public boolean contains(Type type) {
    return beans.contains(type);
  }

  @Override
  public <T> T get(Class<T> type) {
    return getByType(type, null);
  }

  @Override
  public <T> T get(Class<T> type, @Nullable String name) {
    return getByType(type, name);
  }

  @Override
  public <T> T get(Type type, @Nullable String name) {
    return getByType(type, name);
  }

  private <T> T getByType(Type type, @Nullable String name) {
    final T bean = beans.get(type, name);
    if (bean != null) {
      return bean;
    }
    if (parent == null) {
      throw new NoSuchElementException("No bean found for type: " + type + " name: " + name);
    }
    return parent.get(type, name);
  }

  /**
   * Get with a strict match on name for the single entry case.
   */
  @Nullable
  Object getStrict(String name, Type[] types) {
    for (Type type : types) {
      Object match = beans.getStrict(type, name);
      if (match != null) {
        return match;
      }
    }
    if (parent instanceof DBeanScope) {
      DBeanScope dParent = (DBeanScope) parent;
      return dParent.getStrict(name, types);
    }
    return null;
  }

  @Override
  public <T> Optional<T> getOptional(Class<T> type) {
    return getMaybe(type, null);
  }

  @Override
  public <T> Optional<T> getOptional(Type type, @Nullable String name) {
    return getMaybe(type, name);
  }

  private <T> Optional<T> getMaybe(Type type, @Nullable String name) {
    final T bean = beans.get(type, name);
    if (bean != null) {
      return Optional.of(bean);
    }
    if (parent == null) {
      return Optional.empty();
    }
    return parent.getOptional(type, name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Map<String, T> map(Type type) {
    return (Map<String, T>) beans.map(type, parent);
  }

  @Override
  public <T> List<T> list(Class<T> type) {
    return listOf(type);
  }

  @Override
  public <T> List<T> list(Type type) {
    return listOf(type);
  }

  @SuppressWarnings("unchecked")
  private <T> List<T> listOf(Type type) {
    List<T> values = (List<T>) beans.all(type);
    if (parent == null) {
      return values;
    }
    return combine(values, parent.list(type));
  }

  static <T> List<T> combine(List<T> values, List<T> parentValues) {
    if (values.isEmpty()) {
      return parentValues;
    } else if (parentValues.isEmpty()) {
      return values;
    } else {
      values.addAll(parentValues);
      return values;
    }
  }

  @Override
  public <T> List<T> listByPriority(Class<T> type) {
    return listByPriority(type, Priority.class);
  }

  @Override
  public <T> List<T> listByPriority(Class<T> type, Class<? extends Annotation> priorityAnnotation) {
    List<T> list = list(type);
    return list.size() > 1 ? sortByPriority(list, priorityAnnotation) : list;
  }

  private <T> List<T> sortByPriority(List<T> list, final Class<? extends Annotation> priorityAnnotation) {
    boolean priorityUsed = false;
    List<SortBean<T>> tempList = new ArrayList<>(list.size());
    for (T bean : list) {
      SortBean<T> sortBean = new SortBean<>(bean, priorityAnnotation);
      tempList.add(sortBean);
      if (!priorityUsed && sortBean.priorityDefined) {
        priorityUsed = true;
      }
    }
    if (!priorityUsed) {
      // nothing with Priority annotation so return original order
      return list;
    }
    Collections.sort(tempList);
    // unpack into new sorted list
    List<T> sorted = new ArrayList<>(tempList.size());
    for (SortBean<T> sortBean : tempList) {
      sorted.add(sortBean.bean);
    }
    return sorted;
  }

  @Override
  public List<Object> listByAnnotation(Class<?> annotation) {
    final List<Object> values = beans.all(annotation);
    if (parent == null) {
      return values;
    }
    return combine(values, parent.listByAnnotation(annotation));
  }

  DBeanScope start() {
    lock.lock();
    try {
      log.log(Level.TRACE, "firing postConstruct");
      for (Runnable invoke : postConstruct) {
        invoke.run();
      }
    } finally {
      lock.unlock();
    }
    return this;
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (shutdownHook != null && !shutdown) {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      }
      if (!closed) {
        // we only allow one call to preDestroy
        closed = true;
        log.log(Level.TRACE, "firing preDestroy");
        for (AutoCloseable closeable : preDestroy) {
          try {
            closeable.close();
          } catch (Exception e) {
            log.log(Level.ERROR, "Error during PreDestroy lifecycle method", e);
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void shutdown() {
    lock.lock();
    try {
      shutdown = true;
      close();
    } finally {
      lock.unlock();
    }
  }

  private static class ShutdownHook extends Thread {
    private final DBeanScope scope;

    ShutdownHook(DBeanScope scope) {
      this.scope = scope;
    }

    @Override
    public void run() {
      scope.shutdown();
    }
  }

  private static class SortBean<T> implements Comparable<SortBean<T>> {

    private final T bean;

    private boolean priorityDefined;

    private final int priority;

    SortBean(T bean, Class<? extends Annotation> priorityAnnotation) {
      this.bean = bean;
      this.priority = initPriority(priorityAnnotation);
    }

    int initPriority(Class<? extends Annotation> priorityAnnotation) {
      // Avoid adding hard dependency on javax.annotation-api by using reflection
      try {
        Annotation ann = bean.getClass().getDeclaredAnnotation(priorityAnnotation);
        if (ann != null) {
          int priority = (Integer) priorityAnnotation.getMethod("value").invoke(ann);
          priorityDefined = true;
          return priority;
        }
      } catch (Exception e) {
        // If this happens, something has gone very wrong since a non-confirming @Priority was found...
        throw new UnsupportedOperationException("Problem instantiating @Priority", e);
      }
      // Default priority as per javax.ws.rs.Priorities.USER
      // User-level filter/interceptor priority
      return 5000;
    }

    @Override
    public int compareTo(SortBean<T> o) {
      return Integer.compare(priority, o.priority);
    }
  }
}
