/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.nexmark;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * A set of {@link NexmarkConfiguration}s.
 */
public enum NexmarkSuite {
  /**
   * The default.
   */
  DEFAULT(defaultConf()),

  /**
   * Sweep through all queries using the default configuration.
   * 100k/10k events (depending on query).
   */
  SMOKE(smoke()),

  /**
   * As for SMOKE, but with 10m/1m events.
   */
  STRESS(stress()),

  /**
   * As for SMOKE, but with 1b/100m events.
   */
  FULL_THROTTLE(fullThrottle()),

  /**
   * Query 10, at high volume with no autoscaling.
   */
  LONG_RUNNING_LOGGER(longRunningLogger());

  private final ImmutableList<NexmarkConfiguration> configurations;

  NexmarkSuite(ImmutableList<NexmarkConfiguration> configurations) {
    this.configurations = configurations;
  }

  private static ImmutableList<NexmarkConfiguration> defaultConf() {
    return ImmutableList.of(new NexmarkConfiguration());
  }

  private static ImmutableList<NexmarkConfiguration> smoke() {
    return IntStream.range(0, 12).mapToObj(query -> {
      NexmarkConfiguration configuration = NexmarkConfiguration.DEFAULT.copy();
      configuration.query = query;
      configuration.numEvents = 100_000;
      if (query == 4 || query == 6 || query == 9) {
        // Scale back so overall runtimes are reasonably close across all queries.
        configuration.numEvents /= 10;
      }
      return configuration;
    }).collect(collectingAndThen(toList(), ImmutableList::copyOf));
  }

  private static ImmutableList<NexmarkConfiguration> stress() {
    return smoke().stream().map(configuration -> {
      if (configuration.numEvents >= 0) {
        configuration.numEvents *= 1000;
      }
      return configuration;
    }).collect(collectingAndThen(toList(), ImmutableList::copyOf));
  }

  private static ImmutableList<NexmarkConfiguration> fullThrottle() {
    return stress();
  }

  private static ImmutableList<NexmarkConfiguration> longRunningLogger() {
    NexmarkConfiguration configuration = NexmarkConfiguration.DEFAULT.copy();
    configuration.numEventGenerators = 10;

    configuration.query = 10;
    configuration.isRateLimited = true;
    configuration.sourceType = NexmarkUtils.SourceType.PUBSUB;
    configuration.numEvents = 0; // as many as possible without overflow.
    configuration.avgPersonByteSize = 500;
    configuration.avgAuctionByteSize = 500;
    configuration.avgBidByteSize = 500;
    configuration.windowSizeSec = 300;
    configuration.occasionalDelaySec = 360;
    configuration.probDelayedEvent = 0.001;
    configuration.useWallclockEventTime = true;
    configuration.firstEventRate = 60000;
    configuration.nextEventRate = 60000;
    configuration.maxLogEvents = 15000;

    return ImmutableList.of(configuration);
  }

  /**
   * Return the configurations corresponding to this suite. We'll override each configuration
   * with any set command line flags, except for --isStreaming which is only respected for
   * the {@link #DEFAULT} suite.
   */
  public Iterable<NexmarkConfiguration> getConfigurations(NexmarkOptions options) {
    Set<NexmarkConfiguration> results = new LinkedHashSet<>();
    for (NexmarkConfiguration configuration : configurations) {
      NexmarkConfiguration result = configuration.copy();
      result.overrideFromOptions(options);
      results.add(result);
    }
    return results;
  }
}
