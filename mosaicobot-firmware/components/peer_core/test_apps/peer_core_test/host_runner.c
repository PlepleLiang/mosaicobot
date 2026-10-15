/* Reuse the ESP Unity TEST_CASE suite on a native host, without flashing the DUT. */
#include "unity.h"
#include "unity_test_runner.h"
static test_desc_t *tests;
void unity_testcase_register(test_desc_t *test) { test->next = tests; tests = test; }
void setUp(void) {}
void tearDown(void) {}
void unity_run_all_tests(void) {
    for (test_desc_t *t = tests; t != 0; t = t->next) {
        UnityDefaultTestRun(t->fn[0], t->name, t->line);
    }
}
void app_main(void);
int main(void) { app_main(); return Unity.TestFailures != 0; }
