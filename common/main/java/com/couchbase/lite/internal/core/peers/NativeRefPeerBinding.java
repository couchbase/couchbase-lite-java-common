//
// Copyright (c) 2020 Couchbase, Inc.
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
package com.couchbase.lite.internal.core.peers;

/**
 * Email exchange, 2022/5/17
 *
 * Blake:
 * Is the reference that the JNI gets when it creates [such an] object (which the Java code stores as
 * a `long`) *always* the same reference that that object will pass as a parameter when it does a callback
 * to the Java code e.g. kSocketFactory.write or C4ReplicatorParameters.C4ReplicatorDocumentsEndedCallback.
 *
 * Jim:
 * Yes this is a safe assumption
 */
public class NativeRefPeerBinding<T> extends WeakPeerBinding<T> { }
