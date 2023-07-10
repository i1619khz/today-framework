/*
 * Original Author -> Harry Yang (taketoday@foxmail.com) https://taketoday.cn
 * Copyright © Harry Yang & 2017 - 2023 All Rights Reserved.
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

package com.example.infrarun.jvmargs;

import java.lang.management.ManagementFactory;

/**
 * Application used for testing {@code InfraRun}'s JVM argument handling.
 *
 * @author Andy Wilkinson
 */
public class InfraRunJvmArgsApplication {

  protected InfraRunJvmArgsApplication() {

  }

  public static void main(String[] args) {
    int i = 1;
    for (String entry : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      System.out.println(i++ + ". " + entry);
    }
  }

}
