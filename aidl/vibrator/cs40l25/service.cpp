/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <log/log.h>

#include "Hardware.h"
#include "Vibrator.h"

using ::aidl::android::hardware::vibrator::HwApi;
using ::aidl::android::hardware::vibrator::HwCal;
using ::aidl::android::hardware::vibrator::Vibrator;
using ::android::defaultServiceManager;
using ::android::ProcessState;
using ::android::sp;
using ::android::String16;

#if !defined(VIBRATOR_NAME)
#define VIBRATOR_NAME "default"
#endif

int main() {
    auto svc = ndk::SharedRefBase::make<Vibrator>(std::make_unique<HwApi>(),
                                                  std::make_unique<HwCal>());
    const auto svcName = std::string() + svc->descriptor + "/" + VIBRATOR_NAME;

    ProcessState::initWithDriver("/dev/vndbinder");

    auto svcBinder = svc->asBinder();
    binder_status_t status = AServiceManager_addService(svcBinder.get(), svcName.c_str());
    LOG_ALWAYS_FATAL_IF(status != STATUS_OK);

    ProcessState::self()->setThreadPoolMaxThreadCount(1);
    ProcessState::self()->startThreadPool();

    ABinderProcess_setThreadPoolMaxThreadCount(0);
    ABinderProcess_joinThreadPool();

    return EXIT_FAILURE;  // should not reach
}
