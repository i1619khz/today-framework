/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © TODAY & 2017 - 2022 All Rights Reserved.
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see [http://www.gnu.org/licenses/]
 */

package cn.taketoday.retry.backoff;

import java.util.Random;
import java.util.function.Supplier;

import cn.taketoday.retry.RetryContext;

/**
 * Implementation of {@link ExponentialBackOffPolicy}
 * that chooses a random multiple of the interval that would come from a simple
 * deterministic exponential. The random multiple is uniformly distributed between 1 and
 * the deterministic multiplier (so in practice the interval is somewhere between the next
 * and next but one intervals in the deterministic case).
 *
 * This has shown to at least be useful in testing scenarios where excessive contention is
 * generated by the test needing many retries. In test, usually threads are started at the
 * same time, and thus stomp together onto the next interval. Using this
 * {@link BackOffPolicy} can help avoid that scenario.
 *
 * Example: initialInterval = 50 multiplier = 2.0 maxInterval = 3000 numRetries = 5
 *
 * {@link ExponentialBackOffPolicy} yields: [50, 100, 200, 400, 800]
 *
 * {@link ExponentialRandomBackOffPolicy} may yield [76, 151, 304, 580, 901] or [53, 190,
 * 267, 451, 815] (random distributed values within the ranges of [50-100, 100-200,
 * 200-400, 400-800, 800-1600])
 *
 * @author Jon Travis
 * @author Dave Syer
 * @author Chase Diem
 * @since 4.0
 */
@SuppressWarnings("serial")
public class ExponentialRandomBackOffPolicy extends ExponentialBackOffPolicy {

  /**
   * Returns a new instance of {@link BackOffContext},
   * seeded with this policies settings.
   */
  @Override
  public BackOffContext start(RetryContext context) {
    return new ExponentialRandomBackOffContext(getInitialInterval(), getMultiplier(), getMaxInterval(),
            getInitialIntervalSupplier(), getMultiplierSupplier(), getMaxIntervalSupplier());
  }

  protected ExponentialBackOffPolicy newInstance() {
    return new ExponentialRandomBackOffPolicy();
  }

  static class ExponentialRandomBackOffContext extends ExponentialBackOffPolicy.ExponentialBackOffContext {

    private final Random r = new Random();

    public ExponentialRandomBackOffContext(long expSeed, double multiplier, long maxInterval,
            Supplier<Long> expSeedSupplier, Supplier<Double> multiplierSupplier,
            Supplier<Long> maxIntervalSupplier) {

      super(expSeed, multiplier, maxInterval, expSeedSupplier, multiplierSupplier, maxIntervalSupplier);
    }

    @Override
    public synchronized long getSleepAndIncrement() {
      long next = super.getSleepAndIncrement();
      next = (long) (next * (1 + r.nextFloat() * (getMultiplier() - 1)));
      if (next > super.getMaxInterval()) {
        next = super.getMaxInterval();
      }
      return next;
    }

  }

}
