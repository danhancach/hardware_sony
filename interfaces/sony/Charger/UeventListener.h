/*
 * Copyright (C) 2018 The Android Open Source Project
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#ifndef HARDWARE_SONY_CHARGER_UEVENTLISTENER_H
#define HARDWARE_SONY_CHARGER_UEVENTLISTENER_H

#include <android-base/chrono_utils.h>
#include "Charger.h"

namespace aidl {
namespace vendor {
namespace sony {
namespace charger {

/**
 * A class to listen for uevents and report reliability events to
 * the Sony Charger HAL.
 * Runs in a background thread if created with ListenForeverInNewThread().
 * Alternatively, process one message at a time with ProcessUevent().
 */
class UeventListener {
  public:
    UeventListener();
    bool ProcessUevent();  // Process a single Uevent
    void ListenForever();  // Process Uevents forever
    void handlePowerSupplyChange();

  private:
    int uevent_fd_;
};
}  // namespace charger
}  // namespace sony
}  // namespace vendor
}  // namespace aidl

#endif  // HARDWARE_SONY_CHARGER_UEVENTLISTENER_H
