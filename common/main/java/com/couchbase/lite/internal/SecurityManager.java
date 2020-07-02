//
// Copyright (c) 2020 Couchbase, Inc All rights reserved.
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
package com.couchbase.lite.internal;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;


class SecurityManager {
    public KeyPair generateKeyPair(int keySizeInBits) {
        try {
            final KeyPairGenerator keyFactory = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            keyFactory.initialize(keySizeInBits);
            return keyFactory.generateKeyPair();
        }
        catch (NoSuchAlgorithmException | NoSuchProviderException ignore) { }
        return null;
    }
}
