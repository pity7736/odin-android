AVD       := Pixel_7
PACKAGE   := io.sitia.odin
ACTIVITY  := $(PACKAGE)/dev.raiseexception.odin.MainActivity
ADB       := adb
EMULATOR  := emulator

.PHONY: run release clear

run:
	@if ! $(ADB) devices | grep -q emulator; then \
		$(EMULATOR) -avd $(AVD) -no-snapshot-load -gpu swiftshader_indirect &\
		echo "Waiting for emulator to boot..."; \
		$(ADB) wait-for-device; \
		until [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null)" = "1" ]; do sleep 2; done; \
		$(ADB) shell input keyevent 82; \
	fi
	./gradlew installDebug
	$(ADB) shell am start -n $(ACTIVITY)

release:
	./gradlew assembleRelease

clear:
	$(ADB) shell pm clear $(PACKAGE)
