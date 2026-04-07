//
// serial_queue.h
//
// Copyright (c) 2026 Couchbase, Inc All rights reserved.
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

#pragma once

#include <mutex>
#include <queue>
#include <functional>
#include <condition_variable>
#include <thread>

namespace litecore::jni {
    class serial_queue {
    public:
        serial_queue() : _stop(false), _worker([this] { run(); }) {}

        ~serial_queue() {
            {
                std::unique_lock<std::mutex> lock(_mutex);
                _stop = true;
            }

            _cv.notify_one();
            _worker.join();
        }

        void dispatch(std::function<void()> task) {
            {
                std::unique_lock<std::mutex> lock(_mutex);
                _tasks.push(task);
            }

            _cv.notify_one();
        }

        void dispatchJava(std::function<void(JNIEnv*, bool)> task, const char* attachName) {
            // task needs to be copied by value here so that it survives until
            // it is run on the worker thread
            std::function<void()> wrapper = [task, attachName] {
                JNIEnv *env = nullptr;
                jint envState = attachJVM(&env, attachName);
                if ((envState != JNI_OK) && (envState != JNI_EDETACHED))
                    return;

                task(env, envState == JNI_EDETACHED);

                if (envState == JNI_EDETACHED) {
                    detachJVM(attachName);
                }
            };

            dispatch(wrapper);
        }
    private:
        void run() {
            while(true) {
                std::function<void()> task;
                {
                    std::unique_lock<std::mutex> lock(_mutex);
                    _cv.wait(lock, [this] { return _stop || !_tasks.empty(); });
                    if (_stop && _tasks.empty()) return;
                    task = std::move(_tasks.front());
                    _tasks.pop();
                }
                try {
                    task();
                } catch(std::exception& e) {
                    jniLog("Serial queue function threw an exception: %s", e.what());
                } catch(...) {
                    jniLog("Serial queue function had an unspecified error");
                }
            }
        }

        bool _stop;
        std::mutex _mutex;
        std::condition_variable _cv;
        std::queue<std::function<void()>> _tasks;
        std::thread _worker;
    };
}