/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers measurements that generate experimental metrics about memory pools. These metrics will
 * only be generated the preview of stable JVM semantic conventions (e.g. by setting the {@code
 * otel.semconv-stability.opt-in} system property to {@code jvm}) is enabled.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ExperimentalMemoryPools {

  // TODO: use the opentelemetry-semconv classes once we have metrics attributes there
  private static final AttributeKey<String> JVM_MEMORY_POOL_NAME =
      stringKey("jvm.memory.pool.name");
  private static final AttributeKey<String> JVM_MEMORY_TYPE = stringKey("jvm.memory.type");

  private static final String HEAP = "heap";
  private static final String NON_HEAP = "non_heap";

  /** Register observers for java runtime experimental memory metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    return registerObservers(openTelemetry, ManagementFactory.getMemoryPoolMXBeans());
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry, List<MemoryPoolMXBean> poolBeans) {

    if (!SemconvStability.emitStableJvmSemconv()) {
      return emptyList();
    }

    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);
    return singletonList(
        meter
            .upDownCounterBuilder("jvm.memory.init")
            .setDescription("Measure of initial memory requested.")
            .setUnit("By")
            .buildWithCallback(callback(poolBeans)));
  }

  private static Consumer<ObservableLongMeasurement> callback(List<MemoryPoolMXBean> poolBeans) {
    List<Attributes> attributeSets = new ArrayList<>(poolBeans.size());
    for (MemoryPoolMXBean pool : poolBeans) {
      attributeSets.add(
          Attributes.builder()
              .put(JVM_MEMORY_POOL_NAME, pool.getName())
              .put(JVM_MEMORY_TYPE, memoryType(pool.getType()))
              .build());
    }

    return measurement -> {
      for (int i = 0; i < poolBeans.size(); i++) {
        Attributes attributes = attributeSets.get(i);
        MemoryUsage memoryUsage = poolBeans.get(i).getUsage();
        if (memoryUsage == null) {
          // JVM may return null in special cases for MemoryPoolMXBean.getUsage()
          continue;
        }
        long value = memoryUsage.getInit();
        if (value != -1) {
          measurement.record(value, attributes);
        }
      }
    };
  }

  private static String memoryType(MemoryType memoryType) {
    switch (memoryType) {
      case HEAP:
        return HEAP;
      case NON_HEAP:
        return NON_HEAP;
    }
    return "unknown";
  }

  private ExperimentalMemoryPools() {}
}
