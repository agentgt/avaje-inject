package io.avaje.inject;

import java.util.List;

/**
 * Provides a global system wide BeanContext that contains all the bean contexts in the classpath.
 * <p>
 * This will automatically get all the bean contexts and wire them all as necessary. It will use
 * a shutdown hook to fire any <code>@PreDestroy</code> methods on beans.
 * </p>
 *
 * <h3>Example: get a bean</h3>
 * <pre>{@code
 *
 *   CoffeeMaker coffeeMaker = ApplicationScope.get(CoffeeMaker.class);
 *   coffeeMaker.brew();
 *
 * }</pre>
 *
 * <h3>Example: get all the beans implementing an interface</h3>
 * <pre>{@code
 *
 *   // e.g. register all WebRoutes for a web framework
 *
 *   List<WebRoute> routes = ApplicationScope.list(WebRoute.class);
 *
 *   // register all the routes ...
 *
 * }</pre>
 */
public class ApplicationScope {

  private static final BeanContext rootContext = init();

  private static BeanContext init() {
    return BeanContext.newBuilder().build();
  }

  private ApplicationScope() {
    // hide
  }

  /**
   * Return the underlying BeanContext.
   */
  public static BeanContext scope() {
    return rootContext;
  }

  /**
   * Return a single bean given the type.
   *
   * <pre>{@code
   *
   *   CoffeeMaker coffeeMaker = ApplicationScope.getBean(CoffeeMaker.class);
   *   coffeeMaker.brew();
   *
   * }</pre>
   *
   * @param type an interface or bean type
   */
  public static <T> T get(Class<T> type) {
    return rootContext.getBean(type);
  }

  /**
   * Return a single bean given the type and name.
   *
   * <pre>{@code
   *
   *   Heater heater = ApplicationScope.getBean(Heater.class, "electric");
   *   heater.heat();
   *
   * }</pre>
   *
   * @param type an interface or bean type
   * @param name the name qualifier of a specific bean
   */
  public static <T> T get(Class<T> type, String name) {
    return rootContext.getBean(type, name);
  }


  /**
   * Return the list of beans that implement the interface.
   *
   * <pre>{@code
   *
   *   // e.g. register all web routes with web a framework
   *
   *   List<WebRoute> routes = ApplicationScope.list(WebRoute.class);
   *
   * }</pre>
   *
   * @param interfaceType An interface class.
   */
  public static <T> List<T> list(Class<T> interfaceType) {
    return rootContext.getBeans(interfaceType);
  }

  /**
   * Return the list of beans that implement the interface ordering based on <code>@Priority</code>.
   *
   * <pre>{@code
   *
   *   // e.g. register all web routes with web a framework
   *
   *   List<WebRoute> routes = ApplicationScope.listByPriority(WebRoute.class);
   *
   * }</pre>
   *
   * @param interfaceType An interface class.
   */
  public static <T> List<T> listByPriority(Class<T> interfaceType) {
    return rootContext.getBeansByPriority(interfaceType);
  }

  /**
   * Start building a RequestScope.
   *
   * @return The request scope builder
   */
  public static RequestScopeBuilder newRequestScope() {
    return rootContext.newRequestScope();
  }
}
