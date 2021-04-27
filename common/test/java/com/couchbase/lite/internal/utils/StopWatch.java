//
// Copyright (c) 2020, 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal.utils;

import java.util.Locale;


public class StopWatch {
    private long startTime = 0; // nano seconds
    private long stopTime = 0;

    public StopWatch() { this.startTime = System.nanoTime(); }

    public void stop() { this.stopTime = System.nanoTime(); }

    public double getElapsedTimeSecs() { return getElapsedTimeMillis() / 1000.0; }

    public double getElapsedTimeMillis() {
        return ((double) (((stopTime == 0) ? stopTime : System.nanoTime()) - startTime) / 1000000.0);
    }

    public String toString(String what, long count, String item) {
        return String.format(Locale.ENGLISH, "%s; %d %s (took %.3f ms)", what, count, item, getElapsedTimeMillis());
    }
}
