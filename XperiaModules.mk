#
# Copyright (C) 2024 XperiaLabs Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Feature flags (device trees override before including this file)
TARGET_SHIPS_XPERIA_SETTINGS ?= false
TARGET_SHIPS_XPERIA_SETTINGS_MENU ?= false
TARGET_SUPPORTS_IMAGE_ENHANCEMENT ?= false
TARGET_SUPPORTS_BATTERY_CARE ?= false
TARGET_SUPPORTS_CPU_CONTROL ?= false
TARGET_SUPPORTS_HIGH_REFRESH_RATE ?= false
TARGET_SUPPORTS_HIGH_POLLING_RATE_SEC_TS ?= false
TARGET_SUPPORTS_HIGH_POLLING_RATE_LXS_TS ?= false
TARGET_SUPPORTS_SOUND_ENHANCEMENT ?= false
TARGET_SUPPORTS_SOUND_ENHANCEMENT_ADDON ?= false
TARGET_SUPPORTS_SOUND_ENHANCEMENT_DTS ?= false
TARGET_SUPPORTS_EUICC ?= false

PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)/XperiaModules

# Settings app
ifeq ($(TARGET_SHIPS_XPERIA_SETTINGS),true)
PRODUCT_PACKAGES += XperiaSettings
endif

ifeq ($(TARGET_SHIPS_XPERIA_SETTINGS_MENU),true)
PRODUCT_PACKAGES += XperiaSettingsMenu
endif

# Display
ifeq ($(TARGET_SUPPORTS_IMAGE_ENHANCEMENT),true)
PRODUCT_PACKAGES += XperiaDisplay
endif

ifeq ($(TARGET_SUPPORTS_HIGH_REFRESH_RATE),true)
PRODUCT_PACKAGES += XperiaSwitcher
endif

# Battery Care
ifeq ($(TARGET_SUPPORTS_BATTERY_CARE),true)
include hardware/sony/XperiaModules/Charger/XperiaCharger/sepolicy/SEPolicy.mk
PRODUCT_PACKAGES += XperiaCharger
endif

# CPU frequency / idle control
ifeq ($(TARGET_SUPPORTS_CPU_CONTROL),true)
include hardware/sony/XperiaModules/Cpu/XperiaCpu/sepolicy/SEPolicy.mk
PRODUCT_PACKAGES += \
    XperiaCpu \
    init.xperia_cpu.rc
endif

# Touch
ifeq ($(TARGET_SUPPORTS_HIGH_POLLING_RATE_SEC_TS),true)
include hardware/sony/XperiaModules/Touch/XperiaTouchSecTS/sepolicy/SEPolicy.mk
PRODUCT_PACKAGES += \
    XperiaTouchOverlay \
    XperiaTouchSecTS
endif

ifeq ($(TARGET_SUPPORTS_HIGH_POLLING_RATE_LXS_TS),true)
include hardware/sony/XperiaModules/Touch/XperiaTouchLXSTS/sepolicy/SEPolicy.mk
PRODUCT_PACKAGES += \
    XperiaTouchOverlay \
    XperiaTouchLXSTS
endif

# Audio
ifeq ($(TARGET_SUPPORTS_SOUND_ENHANCEMENT),true)
PRODUCT_PACKAGES += XperiaAudio
endif

ifeq ($(TARGET_SUPPORTS_SOUND_ENHANCEMENT_ADDON),true)
ifneq ($(TARGET_SHIPS_SOUND_ENHANCEMENT),false)
PRODUCT_PACKAGES += XperiaAudioPlus
ifneq ($(TARGET_SUPPORTS_360RA),true)
    # XperiaAudioAddon shares com.sonyericsson.soundenhancement with stock
    # SoundEnhancement; use device-tree SoundEnhancementPDX237 instead.
else
PRODUCT_PACKAGES += XperiaAudioAddon
endif
endif
endif

ifeq ($(TARGET_SUPPORTS_SOUND_ENHANCEMENT_DTS),true)
ifeq ($(TARGET_SUPPORTS_360RA),true)
PRODUCT_PACKAGES += \
    XperiaAudioDTS \
    XperiaTSRA
endif
endif

# eUICC
ifeq ($(TARGET_SUPPORTS_EUICC),true)
PRODUCT_PACKAGES += XperiaEuicc
endif
