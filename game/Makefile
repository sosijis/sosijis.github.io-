JAVAC ?= javac
JAVA  ?= java
SRC   := $(shell find src -name '*.java')

.PHONY: all run test shots clean

all:
	@./build.sh

run: all
	@./run.sh

# Безоконный прогон всех режимов на всех картах
test:
	@WITH_TOOLS=1 ./build.sh
	@$(JAVA) -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp build com.sosijis.shooter.SmokeTest 25

# Снимки экрана меню и боя в каталог shots/
shots:
	@WITH_TOOLS=1 ./build.sh
	@$(JAVA) -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp build com.sosijis.shooter.Shots shots

clean:
	rm -rf build shooter.jar shots
