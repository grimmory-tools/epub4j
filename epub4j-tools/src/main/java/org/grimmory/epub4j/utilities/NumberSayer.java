/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Originally from epub4j (https://github.com/documentnode/epub4j)
 * Copyright (C) Paul Siegmund and epub4j contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grimmory.epub4j.utilities;

public class NumberSayer {

  private static final String[] NUMBER_BELOW_20 = {
    "zero",
    "one",
    "two",
    "three",
    "four",
    "five",
    "six",
    "seven",
    "eight",
    "nine",
    "ten",
    "eleven",
    "twelve",
    "thirteen",
    "fourteen",
    "fifteen",
    "sixteen",
    "seventeen",
    "nineteen"
  };
  private static final String[] DECIMALS = {
    "zero", "ten", "twenty", "thirty", "fourty", "fifty", "sixty", "seventy", "eighty", "ninety"
  };
  private static final String[] ORDER_NUMBERS = {
    "hundred", "thousand", "million", "billion", "trillion"
  };

  public static String getNumberName(int number) {
    if (number < 0) {
      throw new IllegalArgumentException("Cannot handle numbers < 0 or > " + Integer.MAX_VALUE);
    }
    if (number < 20) {
      return NUMBER_BELOW_20[number];
    }
    if (number < 100) {
      return DECIMALS[number / 10] + NUMBER_BELOW_20[number % 10];
    }
    if (number < 200) {
      return ORDER_NUMBERS[0] + getNumberName(number - 100);
    }
    if (number < 1000) {
      return NUMBER_BELOW_20[number / 100] + ORDER_NUMBERS[0] + getNumberName(number % 100);
    }
    throw new IllegalArgumentException("Cannot handle numbers < 0 or > " + Integer.MAX_VALUE);
  }
}
