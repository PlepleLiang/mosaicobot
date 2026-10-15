"""Host-only clang checks of production Works deletion and HTTP directory code.
Run directly with Python, or collect with pytest; no firmware build/device required.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def compile_run(tmp_path, name, code, *args):
    compiler = shutil.which("clang")
    assert compiler, "clang is required"
    source = tmp_path / (name + ".c")
    exe = tmp_path / (name + ".exe")
    source.write_text(code, encoding="utf-8")
    result = subprocess.run([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror",
                             str(source), "-o", str(exe)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    result = subprocess.run([str(exe), *map(str, args)], capture_output=True, text=True)
    assert result.returncode == 0, result.stdout + result.stderr
    print(result.stdout.strip())


def test_delete_reservation_and_registry_callbacks(tmp_path):
    source = (ROOT / "components/mosaic_ui/common/works_runtime.c").read_text(encoding="utf-8")
    actual = source[source.index("bool works_runtime_phone_id_is_valid("):]
    prefix = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#define ESP_OK 0
#define ESP_ERR_INVALID_ARG 1
#define ESP_ERR_NOT_FOUND 2
#define ESP_ERR_INVALID_STATE 3
#define ESP_ERR_TIMEOUT 4
#define portMAX_DELAY 1000
#define pdTRUE 1
#define APP_REGISTRY_MANAGE_MODE_READONLY 0
#define WORKS_RUNTIME_APP_ID_MAX 64
#define ID "mb_1234567890abcdef_v1_01234567"
#define ORPHAN "mb_1234567890abcdef_v2_01234567"
typedef int esp_err_t;
typedef enum {WORKS_RUNTIME_STOPPED, WORKS_RUNTIME_QUEUED, WORKS_RUNTIME_RUNNING,
              WORKS_RUNTIME_STOPPING, WORKS_RUNTIME_FAILED} works_runtime_state_t;
typedef struct {const char *id; bool builtin; works_runtime_state_t state;
                char job_id[40], last_error[160];} works_item_t;
typedef struct {int type; char app_id[64], job_id[40];} works_command_t;
typedef struct {const char *app_id; int manage_mode;} app_registry_entry_t;
enum {WORKS_COMMAND_START, WORKS_COMMAND_STOP};
static works_item_t item = {.id=ID};
static struct {int lock, commands; works_item_t *items; size_t item_count;
               char deleting_id[64];} s_runtime = {.lock=1,.commands=1,.items=&item,.item_count=1};
static int locked, removes, refreshes, flushes, registry_error, remove_error, readonly, present=1;
static void xSemaphoreTake(int l,int t){(void)l;(void)t;assert(!locked);locked=1;}
static void xSemaphoreGive(int l){(void)l;assert(locked);locked=0;}
static void strlcpy(char*d,const char*s,size_t n){snprintf(d,n,"%s",s);}
static bool app_registry_id_is_valid(const char *id){return id && strlen(id)<=63;}
static bool state_is_active(works_runtime_state_t s){return s==WORKS_RUNTIME_QUEUED || s==WORKS_RUNTIME_RUNNING || s==WORKS_RUNTIME_STOPPING;}
static works_item_t *find_item_locked(const char*id){assert(locked);return present && !strcmp(id,ID)?&item:NULL;}
static int xQueueSend(int q,const works_command_t*c,int t){(void)q;(void)c;(void)t;assert(locked);return pdTRUE;}
static uint32_t next_revision_locked(void){assert(locked);return 1;}
static void notify_changed(uint32_t r){(void)r;assert(!locked);}
esp_err_t works_runtime_set_running(const char*,bool);
esp_err_t works_runtime_request_toggle(const char*);
esp_err_t works_runtime_remove(const char*);
static esp_err_t app_registry_foreach(esp_err_t (*cb)(const app_registry_entry_t*,void*),void*ctx){
 assert(!locked);if(registry_error)return registry_error;
 app_registry_entry_t e={ID,readonly?0:1};return cb(&e,ctx);
}
static esp_err_t works_runtime_refresh(void){
 assert(!locked);refreshes++;
 /* A synchronous registry callback may take the runtime lock and replace its catalog. */
 xSemaphoreTake(1,0);xSemaphoreGive(1);
 assert(works_runtime_set_running(s_runtime.deleting_id,true)==ESP_ERR_INVALID_STATE);
 return ESP_OK;
}
static esp_err_t works_runtime_flush(void){assert(!locked);flushes++;return ESP_OK;}
static esp_err_t app_registry_remove(const char*id){
 assert(!locked);assert(!strcmp(id,s_runtime.deleting_id));removes++;
 assert(works_runtime_request_toggle(id)==ESP_ERR_INVALID_STATE);
 assert(works_runtime_remove(id)==ESP_ERR_INVALID_STATE);
 if(remove_error)return remove_error;
 assert(works_runtime_refresh()==ESP_OK);present=0;return ESP_OK;
}
'''
    main = r'''
int main(void){
 assert(works_runtime_remove(NULL)==ESP_ERR_INVALID_ARG);
 assert(works_runtime_remove("dino_app")==ESP_ERR_INVALID_ARG);
 assert(works_runtime_remove(ID "/../dino_app")==ESP_ERR_INVALID_ARG);
 item.builtin=true;assert(works_runtime_remove(ID)==ESP_ERR_INVALID_STATE);item.builtin=false;
 for(int s=WORKS_RUNTIME_QUEUED;s<=WORKS_RUNTIME_STOPPING;s++){
  item.state=s;assert(works_runtime_remove(ID)==ESP_ERR_INVALID_STATE);
 }
 item.state=WORKS_RUNTIME_FAILED;strcpy(item.job_id,"cleanup-pending");
 assert(works_runtime_remove(ID)==ESP_ERR_INVALID_STATE);
 item.state=WORKS_RUNTIME_STOPPED;assert(works_runtime_remove(ID)==ESP_ERR_INVALID_STATE);item.job_id[0]=0;
 present=0;readonly=1;assert(works_runtime_remove(ID)==ESP_ERR_INVALID_STATE);
 assert(removes==0 && !s_runtime.deleting_id[0]);readonly=0;present=1;
 registry_error=ESP_ERR_TIMEOUT;assert(works_runtime_remove(ID)==ESP_ERR_TIMEOUT);
 assert(removes==0 && !s_runtime.deleting_id[0]);registry_error=0;
 remove_error=ESP_ERR_TIMEOUT;assert(works_runtime_remove(ID)==ESP_ERR_TIMEOUT);
 assert(present && !s_runtime.deleting_id[0]);remove_error=0;
 assert(works_runtime_remove(ID)==ESP_OK);assert(!present && refreshes==2 && flushes==1);
 assert(!s_runtime.deleting_id[0]);present=1;item.state=WORKS_RUNTIME_STOPPED;
 assert(works_runtime_set_running(ID,true)==ESP_OK);item.state=WORKS_RUNTIME_STOPPED;present=0;
 assert(works_runtime_remove(ORPHAN)==ESP_OK);assert(!s_runtime.deleting_id[0]);
 remove_error=ESP_ERR_NOT_FOUND;assert(works_runtime_remove(ORPHAN)==ESP_OK);
 assert(!s_runtime.deleting_id[0]);puts("Works deletion guards, orphan, callback reservation: PASS");return 0;
}
'''
    compile_run(tmp_path, "works_delete", prefix + actual + main)


def test_http_directory_and_delete_contract(tmp_path):
    source = (ROOT / "components/http_server/http_server_works_api.c").read_text(encoding="utf-8")
    actual = source[source.index("static bool phone_id("):source.index("esp_err_t http_server_register_works_routes(")]
    runtime = (ROOT / "components/mosaic_ui/common/works_runtime.c").read_text(encoding="utf-8")
    validator = runtime[runtime.index("bool works_runtime_phone_id_is_valid("):runtime.index("static esp_err_t reject_readonly(")]
    apps = tmp_path / "apps"
    apps.mkdir()
    orphan = "mb_1234567890abcdef_v2_01234567"
    (apps / orphan).mkdir()
    (apps / "mb_1234567890abcdef_v1_01234567").mkdir()
    (apps / "mb_invalid").mkdir()
    (apps / "dino_app").mkdir()
    (apps / "mb_1234567890abcdef_v3_01234567").write_text("not a directory")
    prefix = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <errno.h>
#include <sys/stat.h>
#define ESP_OK 0
#define ESP_ERR_NOT_FOUND 2
#define ESP_ERR_INVALID_STATE 3
#define ESP_ERR_INVALID_SIZE 4
#define ESP_ERR_NO_MEM 5
#define ESP_FAIL 6
#define HTTPD_400_BAD_REQUEST 400
#define APP_REGISTRY_MANAGE_MODE_READONLY 0
#define WORKS_RUNTIME_APP_ID_MAX 64
#define HTTP_SERVER_PATH_MAX 1024
#define CLAW_PATH_DATA 0
#define ID "mb_1234567890abcdef_v1_01234567"
#define ORPHAN "mb_1234567890abcdef_v2_01234567"
typedef int esp_err_t;
typedef struct {int state;char last_error[160];} works_runtime_item_snapshot_t;
typedef struct {const char *app_id,*display_name,*entry;bool visible;int manage_mode;} app_registry_entry_t;
/* Minimal owning JSON tree substitute; assertions inspect the same production-built fields. */
typedef struct cJSON {char *string,*valuestring;int valueint,type;struct cJSON *child,*next;} cJSON;
static int fail_alloc;
static cJSON *make(int type){if(fail_alloc)return NULL;cJSON*n=calloc(1,sizeof(*n));assert(n);n->type=type;return n;}
static cJSON *cJSON_CreateObject(void){return make(1);}
static bool cJSON_AddItemToArray(cJSON*r,cJSON*n){if(!n)return false;cJSON**p=&r->child;while(*p)p=&(*p)->next;*p=n;return true;}
static cJSON *field(cJSON*r,const char*k,int type){cJSON*n=make(type);if(!n)return NULL;n->string=strdup(k);cJSON_AddItemToArray(r,n);return n;}
static cJSON *cJSON_AddArrayToObject(cJSON*r,const char*k){return field(r,k,2);}
static cJSON *cJSON_AddStringToObject(cJSON*r,const char*k,const char*v){cJSON*n=field(r,k,3);if(n)n->valuestring=strdup(v);return n;}
static cJSON *cJSON_AddBoolToObject(cJSON*r,const char*k,bool v){cJSON*n=field(r,k,4);if(n)n->valueint=v;return n;}
static cJSON *cJSON_AddNumberToObject(cJSON*r,const char*k,int v){cJSON*n=field(r,k,5);if(n)n->valueint=v;return n;}
static cJSON *cJSON_GetObjectItemCaseSensitive(cJSON*r,const char*k){for(cJSON*n=r->child;n;n=n->next)if(!strcmp(n->string,k))return n;return NULL;}
static bool cJSON_IsString(cJSON*n){return n && n->type==3;}
static void cJSON_Delete(cJSON*n){if(!n)return;while(n->child){cJSON*c=n->child;n->child=c->next;cJSON_Delete(c);}free(n->string);free(n->valuestring);free(n);}
#define cJSON_ArrayForEach(n,r) for(n=(r)->child;n;n=n->next)
typedef struct {int content_len,status,query_error;const char *id,*action;cJSON*response;} httpd_req_t;
static int runtime_present=1,remove_error,removed,registry_error;
static const char *apps_root;
static bool app_registry_id_is_valid(const char*s){return s && strlen(s)<=63;}
static esp_err_t works_runtime_get_by_id(const char*id,works_runtime_item_snapshot_t*out){if(runtime_present && !strcmp(id,ID)){out->state=2;return ESP_OK;}return ESP_ERR_NOT_FOUND;}
static esp_err_t app_registry_foreach(esp_err_t(*cb)(const app_registry_entry_t*,void*),void*ctx){
 if(registry_error)return registry_error;
 for(int i=0;i<70;i++){char id[64];snprintf(id,sizeof(id),"mb_1234567890abcdef_v1_%08x",i);
  app_registry_entry_t e={i==0?ID:id,i==0?"Phone work":"Other work","main.lua",true,1};
  esp_err_t err=cb(&e,ctx);if(err)return err;}
 app_registry_entry_t builtin={"dino_app","Dino","main.lua",true,0};
 esp_err_t err=cb(&builtin,ctx);if(err)return err;
 app_registry_entry_t hidden={"hidden","Hidden","main.lua",false,0};return cb(&hidden,ctx);
}
static esp_err_t claw_paths_join(int root,const char*part,char*out,size_t n){assert(root==0 && !strcmp(part,"apps"));snprintf(out,n,"%s",apps_root);return ESP_OK;}
static esp_err_t httpd_resp_send_500(httpd_req_t*r){r->status=500;return 500;}
static esp_err_t httpd_resp_send_err(httpd_req_t*r,int status,const char*s){(void)s;r->status=status;return status;}
static void httpd_resp_set_status(httpd_req_t*r,const char*s){assert(!strcmp(s,"409 Conflict"));r->status=409;}
static esp_err_t http_server_send_json_response(httpd_req_t*r,cJSON*root){r->response=root;return ESP_OK;}
static const char *esp_err_to_name(esp_err_t e){(void)e;return "ESP_ERR_INVALID_STATE";}
static esp_err_t http_server_query_get(httpd_req_t*r,const char*k,char*out,size_t n){assert(!strcmp(k,"id"));if(r->query_error)return r->query_error;snprintf(out,n,"%s",r->id);return ESP_OK;}
static esp_err_t http_server_parse_json_body(httpd_req_t*r,cJSON**out){*out=cJSON_CreateObject();cJSON_AddStringToObject(*out,"id",r->id);cJSON_AddStringToObject(*out,"action",r->action);return ESP_OK;}
static esp_err_t app_registry_publish(const char*id){(void)id;return ESP_OK;}
static esp_err_t works_runtime_set_running(const char*id,bool running){(void)id;(void)running;return ESP_OK;}
static esp_err_t works_runtime_remove(const char*id){assert(!strcmp(id,ID)||!strcmp(id,ORPHAN));removed++;if(!remove_error)runtime_present=0;return remove_error;}
'''
    main = r'''
int main(int argc,char**argv){
 assert(argc==2);apps_root=argv[1];httpd_req_t r={.query_error=ESP_ERR_NOT_FOUND};
 assert(works_get(&r)==ESP_OK);assert(cJSON_GetObjectItemCaseSensitive(r.response,"ok")->valueint==1);
 assert(cJSON_GetObjectItemCaseSensitive(r.response,"protocol")->valueint==1);
 cJSON*items=cJSON_GetObjectItemCaseSensitive(r.response,"items");int count=0;bool builtin=false,orphan=false;
 for(cJSON*n=items->child;n;n=n->next){count++;const char*id=cJSON_GetObjectItemCaseSensitive(n,"id")->valuestring;
  const char*state=cJSON_GetObjectItemCaseSensitive(n,"state")->valuestring;
  if(!strcmp(id,"dino_app")){builtin=true;assert(cJSON_GetObjectItemCaseSensitive(n,"builtin")->valueint==1);}
  if(!strcmp(id,ORPHAN)){orphan=true;assert(!strcmp(state,"not_found"));}
  if(!strcmp(id,ID)){assert(!strcmp(state,"running"));assert(!strcmp(cJSON_GetObjectItemCaseSensitive(n,"display_name")->valuestring,"Phone work"));}
 }
 assert(count==72 && builtin && orphan);cJSON_Delete(r.response);r.response=NULL;
 registry_error=ESP_ERR_INVALID_STATE;assert(works_get(&r)==500);registry_error=0;
 fail_alloc=1;assert(works_get(&r)==500);fail_alloc=0;
 r.query_error=0;r.id="";assert(works_get(&r)==400);
 r.id=ID;r.action="delete";r.status=200;assert(works_post(&r)==ESP_OK);
 assert(removed==1 && cJSON_GetObjectItemCaseSensitive(r.response,"ok")->valueint==1);
 assert(!strcmp(cJSON_GetObjectItemCaseSensitive(r.response,"state")->valuestring,"not_found"));cJSON_Delete(r.response);
 runtime_present=1;remove_error=ESP_ERR_INVALID_STATE;r.status=200;assert(works_post(&r)==ESP_OK);
 assert(r.status==409 && !cJSON_GetObjectItemCaseSensitive(r.response,"ok")->valueint);cJSON_Delete(r.response);
 r.id="dino_app";assert(works_post(&r)==400);assert(removed==2);
 r.id=ID;r.action="unknown";assert(works_post(&r)==400);r.content_len=513;assert(works_post(&r)==400);
 puts("Works HTTP registry >64, builtin, orphan, delete receipts: PASS");return 0;
}
'''
    compile_run(tmp_path, "works_http", prefix + validator + actual + main, apps)


if __name__ == "__main__":
    with tempfile.TemporaryDirectory(prefix="works-management-") as directory:
        path = Path(directory)
        test_delete_reservation_and_registry_callbacks(path)
        test_http_directory_and_delete_contract(path)
