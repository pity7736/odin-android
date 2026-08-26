AVD       := Pixel_7
PACKAGE   := dev.raiseexception.odin
ACTIVITY  := $(PACKAGE)/.MainActivity
ADB       := adb
EMULATOR  := emulator

.PHONY: run

run:
	@if ! $(ADB) devices | grep -q emulator; then \
		$(EMULATOR) -avd $(AVD) -no-snapshot-load &\
		echo "Waiting for emulator to boot..."; \
		$(ADB) wait-for-device; \
		until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 2; done; \
		$(ADB) shell input keyevent 82; \
	fi
	./gradlew installDebug
	$(ADB) shell am start -n $(ACTIVITY)
