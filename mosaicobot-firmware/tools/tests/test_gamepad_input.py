"""Run production gamepad state/poll code with fake clock, Lua and RTOS APIs."""
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def test_stale_input_preserves_session_and_never_replays_start(tmp_path):
    source = (ROOT / "components/lua_module_gamepad/src/lua_module_gamepad.c").read_text(encoding="utf-8")
    source = re.sub(r'^#include .*$', '', source, flags=re.M)
    harness = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "cJSON.h"
typedef int esp_err_t;
typedef int SemaphoreHandle_t;
typedef void *esp_timer_handle_t;
typedef int wifi_ps_type_t;
typedef struct {int unused;} lua_State;
typedef struct {const char *name;int (*fn)(lua_State *);} luaL_Reg;
typedef struct {void (*callback)(void *);const char *name;} esp_timer_create_args_t;
#define ESP_OK 0
#define ESP_ERR_NO_MEM 1
#define WIFI_PS_NONE 0
#define portMAX_DELAY 0
#define ESP_LOGI(...) ((void)0)
#define ESP_LOGW(...) ((void)0)
static int64_t now;
static bool bool_value, connected_value, active_value;
static int64_t esp_timer_get_time(void) {return now;}
static int xSemaphoreTake(int a,int b) {(void)a;(void)b;return 1;}
static int xSemaphoreGive(int a) {(void)a;return 1;}
static int xSemaphoreCreateMutex(void) {return 1;}
static int esp_timer_create(const esp_timer_create_args_t *a,esp_timer_handle_t *b) {(void)a;*b=(void *)1;return 0;}
static int esp_timer_start_periodic(esp_timer_handle_t a,int b) {(void)a;(void)b;return 0;}
static int esp_timer_delete(esp_timer_handle_t a) {(void)a;return 0;}
static int esp_wifi_get_ps(int *p) {*p=1;return 0;}
static int esp_wifi_set_ps(int p) {(void)p;return 0;}
static const char *esp_err_to_name(int p) {(void)p;return "ok";}
static void esp_fill_random(void *p,size_t n) {memset(p,0x12,n);}
static int cap_lua_runtime_job_id(lua_State *L) {(void)L;return 1;}
static int cap_lua_register_exit_cleanup(void (*fn)(lua_State *)) {(void)fn;return 0;}
static int cap_lua_register_module(const char *s,int (*fn)(lua_State *)) {(void)s;(void)fn;return 0;}
static int luaL_error(lua_State *L,const char *s) {(void)L;(void)s;abort();}
static void lua_createtable(lua_State *L,int a,int b) {(void)L;(void)a;(void)b;}
static void lua_pushboolean(lua_State *L,int b) {(void)L;bool_value=b;}
static void lua_pushnumber(lua_State *L,double d) {(void)L;(void)d;}
static void lua_setfield(lua_State *L,int i,const char *key) {
 (void)L;(void)i;
 if(!strcmp(key,"connected")) connected_value=bool_value;
 if(!strcmp(key,"input_active")) active_value=bool_value;
}
static void lua_rawseti(lua_State *L,int i,int j) {(void)L;(void)i;(void)j;}
static void luaL_newlib(lua_State *L,const luaL_Reg *r) {(void)L;(void)r;}
'''
    main = r'''
static void input(int fd,int seq,int held) {
 cJSON *p=cJSON_CreateObject();
 cJSON_AddNumberToObject(p,"v",1);cJSON_AddStringToObject(p,"type","state");
 cJSON_AddStringToObject(p,"token",s.token);cJSON_AddStringToObject(p,"run",s.run);
 cJSON_AddNumberToObject(p,"seq",seq);cJSON_AddNumberToObject(p,"held",held);
 cJSON_AddNumberToObject(p,"lx",0.75);cJSON_AddNumberToObject(p,"ly",0);
 cJSON_AddNumberToObject(p,"rx",0);cJSON_AddNumberToObject(p,"ry",0);
 cJSON *r=gamepad_exchange(fd,p);cJSON_Delete(r);cJSON_Delete(p);
}
static void claim(int fd) {
 assert(gamepad_connect(fd));
 cJSON *p=cJSON_Parse("{\"v\":1,\"type\":\"poll\"}");
 cJSON *r=gamepad_exchange(fd,p);cJSON_Delete(r);cJSON_Delete(p);
}
int main(void) {
 lua_State L={0};assert(lua_module_gamepad_register(NULL)==0);pad_open(&L);
 pad_poll(&L);assert(!connected_value && !active_value);
 claim(4);pad_poll(&L);assert(!connected_value && !active_value); // first sample required
 input(4,1,0);pad_poll(&L);assert(connected_value && active_value);
 input(4,2,8192);assert(s.edge_count==1 && s.edges[0].pressed==8192);
 pad_poll(&L);assert(s.edge_count==0);
 now=350000;pad_poll(&L);assert(connected_value && active_value);
 now=350001;pad_poll(&L);assert(connected_value && !active_value);
 assert(s.held==0 && s.axes[0]==0 && s.edge_count==0 && s.seq==2 && s.owner==4);
 input(4,2,8192);assert(!s.input_live); // duplicate does not revive input/watchdog
 now=650000;input(4,3,8192);
 assert(s.input_live && s.held==8192 && s.edge_count==0); // held Start NOT a fresh press
 pad_poll(&L);assert(connected_value && active_value);
 now=1100000;pad_poll(&L);assert(connected_value && !active_value);
 input(4,4,0);assert(s.edges[0].pressed==0 && s.edges[0].released==8192);
 pad_poll(&L);input(4,5,8192);assert(s.edges[0].pressed==8192); // release + repress works
 pad_poll(&L);
 now=3600000;pad_poll(&L);assert(connected_value && !active_value); // exactly 2.5s retained
 now=3600001;pad_poll(&L);assert(!connected_value && !active_value);
 assert(s.owner==-1 && s.held==0 && s.seq==0 && s.edge_baseline==0);
 claim(5);input(5,1,0);pad_poll(&L);assert(connected_value && active_value);
 gamepad_disconnect(4);pad_poll(&L);assert(connected_value); // old socket cannot release new
 gamepad_disconnect(5);pad_poll(&L);assert(!connected_value && !active_value);
 claim(6);input(6,1,0);pad_close(&L);pad_poll(&L);assert(!connected_value && !active_value);
 puts("gamepad stale/session/edge-baseline boundaries: PASS");
}
'''
    cfile = tmp_path / "gamepad_input.c"
    cfile.write_text(harness + source + main, encoding="utf-8")
    cjson = ROOT / "managed_components/espressif__cjson/cJSON"
    exe = tmp_path / "gamepad_input.exe"
    compiled = subprocess.run([shutil.which("clang"), "-Wall", "-Wextra", "-Wno-unused-function",
                    "-I", str(cjson), str(cfile), str(cjson / "cJSON.c"), "-o", str(exe)],
                   capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stderr
    executed = subprocess.run([str(exe)], capture_output=True, text=True)
    assert executed.returncode == 0, executed.stdout + executed.stderr
