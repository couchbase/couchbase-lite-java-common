//
// Copyright (c) 2020 Couchbase, Inc.
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.internal.core;

/**
 * This is a simplified version of the C4PredictiveModel:
 * 1. No context object is passed to the callback predict() method.
 * 2. The predict() method does not return the error. Any error will be logged from
 * the Java code. If this causes confusion we could make the predict method return
 * a complex object that includes a C4Error.
 */
public interface C4PredictiveModel {
    /* FLSliceResult */
    long predict(
        /* FLDict */ long input,
        /* C4Database */ long c4db);
}

