package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object invokedTargetObject;
  private final ProfilingState methodProfilingState;

  ProfilingMethodInterceptor(Clock clock, Object targetObject, ProfilingState methodProfilingState) {
    this.clock = Objects.requireNonNull(clock);
    this.invokedTargetObject = Objects.requireNonNull(targetObject);
    this.methodProfilingState = Objects.requireNonNull(methodProfilingState);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // If the method contains @Profiled annotation record the invoked method call duration using ProfileState
    if (method.isAnnotationPresent(Profiled.class)) {
      Instant methodCallStartTime = clock.instant();
      Object invokedResultObject;
      try {
        invokedResultObject = method.invoke(invokedTargetObject, args);
      } catch (InvocationTargetException e) {
        throw e.getTargetException();
      } catch (Exception e) {
        throw e.getCause();
      } finally {
        methodProfilingState.record(invokedTargetObject.getClass(), method, Duration.between(methodCallStartTime, clock.instant()));
      }
      return invokedResultObject;
    }

    // If the method does not contains the @Profiled annotation invoke the method without recording method call duration
    return method.invoke(invokedTargetObject, args);
  }
}
