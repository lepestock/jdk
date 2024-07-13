GENERATOR_ARGS =
ifneq "x$(SEED)" "x"
	GENERATOR_ARGS += -z $(SEED)
endif

clean_generated:
	@rm -rf generated

generate_test: COMPILE
	$(JAVA) -cp build/classes \
    --add-opens java.base/java.util=ALL-UNNAMED \
    jdk.test.lib.jittester.JavaCodeGenerator \
    --classes-file conf/classes.lst \
    --exclude-methods-file conf/exclude.methods.lst \
    --testbase-dir generated \
    --temp-dir tmp \
    --print-hierarchy true \
    $(GENERATOR_ARGS) \
    --main-class Test_0

run_test: clean_generated generate_test
	$(JAVAC) -cp build/classes \
	    -nowarn \
	    generated/java_tests/Test_0.java

#interesting seeds:
# 32752240386188 - causes strange crash of the generator
