"""Execute the production Works command function with bounded FreeRTOS substitutes."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def test_remote_commands_are_explicit_and_preserve_other_jobs(tmp_path):
    source = (ROOT / "components/mosaic_ui/common/works_runtime.c").read_text(encoding="utf-8")
    actual = source[source.index("static esp_err_t request_state("):]
    harness = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#define ESP_OK 0
#define ESP_ERR_INVALID_ARG 1
#define ESP_ERR_NOT_FOUND 2
#define ESP_ERR_INVALID_STATE 3
#define ESP_ERR_TIMEOUT 4
#define portMAX_DELAY 1000
#define pdTRUE 1
#define WORKS_RUNTIME_ERROR_MAX 160
#define WORKS_RUNTIME_APP_ID_MAX 64
#define CAP_LUA_JOB_ID_LEN 40
typedef int esp_err_t;
typedef enum {WORKS_RUNTIME_STOPPED, WORKS_RUNTIME_QUEUED, WORKS_RUNTIME_RUNNING, WORKS_RUNTIME_STOPPING, WORKS_RUNTIME_FAILED} works_runtime_state_t;
typedef struct { char *id; works_runtime_state_t state; char job_id[40]; char last_error[160]; } works_item_t;
typedef struct { int type; char app_id[64]; char job_id[40]; } works_command_t;
enum {WORKS_COMMAND_START, WORKS_COMMAND_STOP};
static works_item_t items[2] = {{.id="one"},{.id="two"}};
static struct {int lock, commands; works_item_t *items; size_t item_count; char deleting_id[64];} s_runtime = {.lock=1,.commands=1,.items=items,.item_count=2};
static int queued, full, locked; static works_command_t command;
static void xSemaphoreTake(int unused, int delay) {(void)unused;(void)delay;assert(!locked);locked=1;}
static void xSemaphoreGive(int unused) {(void)unused;assert(locked);locked=0;}
static int xQueueSend(int unused, const works_command_t *c, int delay) {(void)unused;(void)delay;assert(locked);if(full)return 0;command=*c;queued++;return 1;}
static void strlcpy(char *d,const char*s,size_t n){snprintf(d,n,"%s",s);}
static works_item_t *find_item_locked(const char *id){assert(locked);for(int i=0;i<2;i++)if(!strcmp(items[i].id,id))return &items[i];return NULL;}
static uint32_t next_revision_locked(void){assert(locked);return 1;}
static void notify_changed(uint32_t r){(void)r;assert(!locked);}
static bool state_is_active(works_runtime_state_t s){return s==WORKS_RUNTIME_QUEUED||s==WORKS_RUNTIME_RUNNING||s==WORKS_RUNTIME_STOPPING;}
'''
    main = r'''
int main(void) {
 assert(works_runtime_set_running(NULL,true)==ESP_ERR_INVALID_ARG);
 assert(works_runtime_set_running("missing",true)==ESP_ERR_NOT_FOUND);
 assert(works_runtime_set_running("one",true)==ESP_OK);
 assert(queued==1 && items[0].state==WORKS_RUNTIME_QUEUED && command.type==WORKS_COMMAND_START);
 assert(works_runtime_set_running("one",true)==ESP_OK && queued==1);
 items[0].state=WORKS_RUNTIME_RUNNING;
 assert(works_runtime_set_running("one",true)==ESP_OK && queued==1);
 assert(works_runtime_set_running("two",true)==ESP_ERR_INVALID_STATE && queued==1);
 strcpy(items[0].job_id,"job-1");
 assert(works_runtime_set_running("one",false)==ESP_OK && queued==2);
 assert(command.type==WORKS_COMMAND_STOP && !strcmp(command.job_id,"job-1"));
 assert(works_runtime_set_running("one",false)==ESP_OK && queued==2);
 assert(works_runtime_set_running("one",true)==ESP_ERR_INVALID_STATE);
 items[0].state=WORKS_RUNTIME_STOPPED;
 assert(works_runtime_set_running("one",false)==ESP_OK && queued==2);
 full=1; assert(works_runtime_set_running("one",true)==ESP_ERR_TIMEOUT);
 assert(items[0].state==WORKS_RUNTIME_STOPPED);
 items[0].state=WORKS_RUNTIME_RUNNING;
 assert(works_runtime_set_running("one",false)==ESP_ERR_TIMEOUT && items[0].state==WORKS_RUNTIME_RUNNING);
 full=0; assert(works_runtime_request_toggle("one")==ESP_OK && command.type==WORKS_COMMAND_STOP);
 puts("Works explicit start/stop, duplicate, conflict, rollback: PASS");
 return 0;
}
'''
    cfile = tmp_path / "works_commands.c"
    cfile.write_text(harness + actual + main, encoding="utf-8")
    exe = tmp_path / "works_commands.exe"
    subprocess.run([shutil.which("clang"), "-Wall", "-Wextra", "-Werror", str(cfile), "-o", str(exe)], check=True, capture_output=True)
    subprocess.run([str(exe)], check=True, capture_output=True)


def test_service_is_prepared_before_ui_and_bound_before_http_routes():
    ui = (ROOT / "components/mosaic_ui/mosaic_ui.c").read_text(encoding="utf-8")
    http = (ROOT / "components/http_server/http_server_works_api.c").read_text(encoding="utf-8")
    runtime = (ROOT / "components/mosaic_ui/common/works_runtime.c").read_text(encoding="utf-8")
    assert ui.index("works_runtime_prepare()") < ui.index("mosaic_loader_init(&loader_config)")
    assert http.index("works_runtime_init(NULL)") < http.index("httpd_register_uri_handler")
    init = runtime[runtime.index("esp_err_t works_runtime_init("):runtime.index("esp_err_t works_runtime_get_revision(")]
    assert "if (config)" in init  # HTTP init must not erase the page callback.


def test_only_immutable_phone_package_ids_are_exposed(tmp_path):
    source = (ROOT / "components/http_server/http_server_works_api.c").read_text(encoding="utf-8")
    runtime = (ROOT / "components/mosaic_ui/common/works_runtime.c").read_text(encoding="utf-8")
    function = runtime[runtime.index("bool works_runtime_phone_id_is_valid("):runtime.index("static esp_err_t reject_readonly(")]
    function += source[source.index("static bool phone_id("):source.index("static esp_err_t snapshot(")]
    prefix = '#include <stdbool.h>\n#include <string.h>\n#include <assert.h>\nstatic bool app_registry_id_is_valid(const char *s) { return s && strlen(s) <= 63; }\n'
    main = r'''
int main(void) {
 assert(phone_id("mb_1234567890abcdef_v1_01234567"));
 assert(phone_id("mb_1234567890abcdef_v123_01234567"));
 assert(!phone_id("dino_app")); assert(!phone_id(NULL));
 const char *valid="mb_1234567890abcdef_v1_01234567";
 char truncated[64];
 for(size_t i=0;i<strlen(valid);i++){memcpy(truncated,valid,i);truncated[i]=0;assert(!phone_id(truncated));}
 assert(!phone_id("mb_1234567890abcdef_v0_01234567"));
 assert(!phone_id("mb_1234567890abcdef_v1_01234567/../dino_app"));
 return 0;
}
'''
    cfile = tmp_path / "ids.c"
    cfile.write_text(prefix + function + main, encoding="utf-8")
    exe = tmp_path / "ids.exe"
    subprocess.run([shutil.which("clang"), "-Wall", "-Werror", str(cfile), "-o", str(exe)], check=True, capture_output=True)
    subprocess.run([str(exe)], check=True, capture_output=True)
