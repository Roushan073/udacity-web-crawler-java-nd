package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  @Override
  public <T> T wrap(Class<T> klass, T targetObject) {
    Objects.requireNonNull(klass);

    // To avoid TestCase Failure, throwing IllegalArgumentException if the wrapped interface does not contain a @Profiled method
    if (Arrays.stream(klass.getMethods()).noneMatch(method -> method.isAnnotationPresent(Profiled.class))) {
      throw new IllegalArgumentException("Class does not contain any @Profiled Annotated methods");
    }

    @SuppressWarnings("unchecked")
    T methodProfilerProxy = (T) Proxy.newProxyInstance(
            klass.getClassLoader(),
            new Class<?>[] { klass },
            new ProfilingMethodInterceptor(clock, targetObject, state)
    );

    return methodProfilerProxy;
  }

  @Override
  public void writeData(Path path) throws IOException {
    try(Writer profileWriter = Files.newBufferedWriter(path, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
      profileWriter.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
      profileWriter.write(System.lineSeparator());
      state.write(profileWriter);
      profileWriter.write(System.lineSeparator());
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
