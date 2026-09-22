AVD       := Pixel_7
PACKAGE   := io.sitia.odin
ACTIVITY  := $(PACKAGE)/dev.raiseexception.odin.MainActivity
ADB       := adb
EMULATOR  := emulator
APK_RELEASE := app/build/outputs/apk/release/app-release.apk

.PHONY: run run-release build release clear

run:
	@if ! $(ADB) devices | grep -q emulator; then \
		$(EMULATOR) -avd $(AVD) -no-snapshot-load -gpu swiftshader_indirect &\
		echo "Waiting for emulator to boot..."; \
		$(ADB) wait-for-device; \
		until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 2; done; \
		$(ADB) shell input keyevent 82; \
	fi
	-$(ADB) uninstall $(PACKAGE) 2>/dev/null
	./gradlew installDebug
	$(ADB) shell am start -n $(ACTIVITY)

run-release:
	@if ! $(ADB) devices | grep -q emulator; then \
		$(EMULATOR) -avd $(AVD) -no-snapshot-load -gpu swiftshader_indirect &\
		echo "Waiting for emulator to boot..."; \
		$(ADB) wait-for-device; \
		until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 2; done; \
		$(ADB) shell input keyevent 82; \
	fi
	-$(ADB) uninstall $(PACKAGE) 2>/dev/null
	./gradlew assembleRelease
	$(ADB) install $(APK_RELEASE)
	$(ADB) shell am start -n $(ACTIVITY)

build:
	./gradlew assembleDebug

release:
	./gradlew assembleRelease

clear:
	$(ADB) shell pm clear $(PACKAGE)
