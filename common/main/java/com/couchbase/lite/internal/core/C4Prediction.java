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

public final class C4Prediction {
    private C4Prediction() {}

    public static void register(String name, C4PredictiveModel model) { registerModel(name, model); }

    public static void unregister(String name) { unregisterModel(name); }

    private static native void registerModel(String name, C4PredictiveModel model);

    private static native void unregisterModel(String name);
}
