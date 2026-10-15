#include "lua_module_gamepad.h"
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "cap_lua.h"
#include "lauxlib.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "esp_wifi.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

#define PAD_WATCHDOG_US 350000
#define PAD_CONNECT_US 2500000
#define PAD_EDGES 32
#define PAD_TOKEN_LEN 33
/* One display runtime, one controller (waiting for a game or active). */
static struct {
    SemaphoreHandle_t lock;
    esp_timer_handle_t watchdog;
    lua_State *runtime;
    char run[PAD_TOKEN_LEN];
    int owner, waiting;
    char token[PAD_TOKEN_LEN];
    int64_t last_input, last_poll;
    uint32_t seq;
    bool input_live;
    bool wifi_ps_saved;
    wifi_ps_type_t wifi_ps;
    uint16_t held, edge_baseline; /* Keep physical key history across brief input expiry. */
    float axes[4];
    struct { uint16_t pressed, released; } edges[PAD_EDGES];
    unsigned edge_count;
} s = {.owner=-1, .waiting=-1};
static const char *keys[] = {"up","down","left","right","a","b","x","y",
    "l1","r1","l2","r2","select","start","l3","r3"};

static void random_token(char *out)
{
    uint32_t r[4]; esp_fill_random(r, sizeof(r));
    snprintf(out, PAD_TOKEN_LEN, "%08lx%08lx%08lx%08lx", (unsigned long)r[0],
        (unsigned long)r[1], (unsigned long)r[2], (unsigned long)r[3]);
}
static void zero_locked(void)
{
    s.held=0; memset(s.axes,0,sizeof(s.axes)); s.edge_count=0; s.input_live=false;
}
static void revoke_locked(void)
{
    zero_locked(); s.edge_baseline=0; s.seq=0; s.owner=-1; s.token[0]=0;
}
static void expire_locked(void)
{
    int64_t now=esp_timer_get_time();
    /* Stop stale input promptly, but do not tear down a live LAN session on a short stall.
     * Keep seq so delayed/duplicate packets cannot roll the input stream backwards. */
    if (s.owner>=0 && now-s.last_input>PAD_CONNECT_US) {
        ESP_LOGW("gamepad","session lost: input age=%lldms",(long long)((now-s.last_input)/1000));
        revoke_locked();
    } else if (s.owner>=0 && s.input_live && now-s.last_input>PAD_WATCHDOG_US) {
        ESP_LOGW("gamepad","input stale: seq=%lu age=%lldms; session retained",
            (unsigned long)s.seq,(long long)((now-s.last_input)/1000));
        zero_locked();
    }
    if (s.waiting>=0 && now-s.last_poll>PAD_CONNECT_US) s.waiting=-1;
}
static void watchdog(void *unused)
{
    (void)unused;
    xSemaphoreTake(s.lock,portMAX_DELAY); expire_locked(); xSemaphoreGive(s.lock);
}
bool gamepad_connect(int fd)
{
    if (!s.lock) return false;
    xSemaphoreTake(s.lock,portMAX_DELAY); expire_locked();
    bool ok=s.waiting<0 && s.owner<0;
    if (ok) {
        s.waiting=fd; s.last_poll=esp_timer_get_time();
    }
    xSemaphoreGive(s.lock); return ok;
}
void gamepad_disconnect(int fd)
{
    if (!s.lock) return;
    xSemaphoreTake(s.lock,portMAX_DELAY);
    if (s.owner==fd) revoke_locked();
    if (s.waiting==fd) s.waiting=-1;
    xSemaphoreGive(s.lock);
}
static bool string_is(const cJSON *p,const char *key,const char *expected)
{
    const cJSON *v=cJSON_GetObjectItemCaseSensitive(p,key);
    return cJSON_IsString(v) && strcmp(v->valuestring,expected)==0;
}
static bool number(const cJSON *p,const char *key,double min,double max,double *out)
{
    const cJSON *v=cJSON_GetObjectItemCaseSensitive(p,key);
    if (!cJSON_IsNumber(v)||!isfinite(v->valuedouble)||v->valuedouble<min||v->valuedouble>max) return false;
    *out=v->valuedouble; return true;
}
cJSON *gamepad_exchange(int fd,const cJSON *p)
{
    if (!s.lock) return NULL;
    cJSON *r=cJSON_CreateObject(); if (!r) return NULL;
    xSemaphoreTake(s.lock,portMAX_DELAY); expire_locked();
    double version=0; bool valid=cJSON_IsObject(p)&&number(p,"v",1,1,&version);
    bool input=string_is(p,"type","state");
    bool accepted=false;
    if (!valid || (!input && !string_is(p,"type","poll") && !string_is(p,"type","release"))) {
        if(s.owner==fd) revoke_locked();
        if(s.waiting==fd) s.waiting=-1;
    } else if (string_is(p,"type","release")) {
        if(s.owner==fd) revoke_locked();
        if(s.waiting==fd) s.waiting=-1;
    } else if (string_is(p,"type","poll") && s.waiting==fd) {
        s.last_poll=esp_timer_get_time();
        if (s.runtime) {
            revoke_locked(); s.owner=fd; s.waiting=-1;
            random_token(s.token); s.last_input=s.last_poll;
        }
    } else if (input && s.owner==fd) {
        double seq,held; float axes[4]; const char *names[]={"lx","ly","rx","ry"};
        valid=string_is(p,"token",s.token)&&string_is(p,"run",s.run)&&
            number(p,"seq",1,2147483647,&seq)&&floor(seq)==seq&&
            number(p,"held",0,65535,&held)&&floor(held)==held;
        for (int i=0;i<4 && valid;i++) { double a; valid=number(p,names[i],-1,1,&a); if(valid) axes[i]=(float)a; }
        if (!valid) revoke_locked();
        else if ((uint32_t)seq>s.seq) {
            uint16_t next=(uint16_t)held;
            if((next & 3U)==3U) next &= ~3U;
            if((next & 12U)==12U) next &= ~12U;
            /* A heartbeat restoring a held Start after a stall is NOT a new press. */
            uint16_t pressed=next & ~s.edge_baseline, released=s.edge_baseline & ~next;
            /* Preserve changes, including a short tap between two Lua frames. Never replay overflow. */
            if ((pressed||released) && s.edge_count==PAD_EDGES) revoke_locked();
            else {
                if(pressed||released) { s.edges[s.edge_count].pressed=pressed; s.edges[s.edge_count++].released=released; }
                if (!s.input_live && s.seq>0) ESP_LOGI("gamepad","input resumed: age=%lldms; same session",
                    (long long)((esp_timer_get_time()-s.last_input)/1000));
                s.held=next; s.edge_baseline=next; memcpy(s.axes,axes,sizeof(axes)); s.seq=(uint32_t)seq;
                s.last_input=esp_timer_get_time(); s.input_live=true; accepted=true;
            }
        }
    }
    const char *status=s.owner==fd ? "ready" : s.waiting==fd ? "waiting_game" : "released";
    cJSON_AddNumberToObject(r,"v",1); cJSON_AddStringToObject(r,"status",status);
    cJSON_AddStringToObject(r,"access","automatic");
    cJSON_AddStringToObject(r,"run",s.run);
    cJSON_AddNumberToObject(r,"seq",s.owner==fd?s.seq:0);
    cJSON_AddBoolToObject(r,"accepted",accepted);
    if(s.owner==fd) cJSON_AddStringToObject(r,"token",s.token);
    xSemaphoreGive(s.lock); return r;
}
static bool owns(lua_State *L) { return s.runtime==L; }
static int pad_open(lua_State *L)
{
    if (!cap_lua_runtime_job_id(L)) return luaL_error(L,"gamepad requires an async Lua work");
    xSemaphoreTake(s.lock,portMAX_DELAY);
    if (s.runtime && !owns(L)) { xSemaphoreGive(s.lock); return luaL_error(L,"gamepad runtime busy"); }
    if (!owns(L)) {
        revoke_locked(); s.runtime=L; random_token(s.run);
        /* A real-time game must not wait for the STA modem sleep/DTIM window.
         * Keep this runtime-only; never change the saved user power profile. */
        s.wifi_ps_saved=esp_wifi_get_ps(&s.wifi_ps)==ESP_OK;
        if (s.wifi_ps_saved) {
            esp_err_t err=esp_wifi_set_ps(WIFI_PS_NONE);
            if (err==ESP_OK) ESP_LOGI("gamepad","Wi-Fi game latency mode: %d -> none",(int)s.wifi_ps);
            if (err!=ESP_OK) {
                s.wifi_ps_saved=false;
                ESP_LOGW("gamepad","Wi-Fi low latency unavailable: %s",esp_err_to_name(err));
            }
        }
    }
    xSemaphoreGive(s.lock); lua_pushboolean(L,1); return 1;
}
static void cleanup(lua_State *L)
{
    xSemaphoreTake(s.lock,portMAX_DELAY);
    if (owns(L)) {
        revoke_locked(); s.runtime=NULL; s.run[0]=0; s.waiting=-1;
        wifi_ps_type_t current;
        if (s.wifi_ps_saved && esp_wifi_get_ps(&current)==ESP_OK && current==WIFI_PS_NONE) {
            esp_err_t err=esp_wifi_set_ps(s.wifi_ps);
            if (err!=ESP_OK) ESP_LOGW("gamepad","Wi-Fi power restore failed: %s",esp_err_to_name(err));
        }
        s.wifi_ps_saved=false;
    }
    xSemaphoreGive(s.lock);
}
static int pad_close(lua_State *L) { cleanup(L); return 0; }
static void push_keys(lua_State *L,uint16_t mask)
{
    lua_createtable(L,0,16);
    for(int i=0;i<16;i++) { lua_pushboolean(L,(mask & (1U<<i))!=0); lua_setfield(L,-2,keys[i]); }
}
static int pad_poll(lua_State *L)
{
    /* Copy a bounded snapshot before calling allocating Lua APIs (which may longjmp). */
    uint16_t held=0,pressed=0,released=0,edges[PAD_EDGES][2];
    float axes[4]={0}; unsigned count=0; bool connected=false, input_active=false;
    xSemaphoreTake(s.lock,portMAX_DELAY); expire_locked();
    if (owns(L)) {
        connected=s.owner>=0 && s.seq>0; input_active=connected && s.input_live;
        held=s.held; memcpy(axes,s.axes,sizeof(axes));
        count=s.edge_count;
        for(unsigned i=0;i<count;i++) {
            edges[i][0]=s.edges[i].pressed; edges[i][1]=s.edges[i].released;
            pressed|=edges[i][0]; released|=edges[i][1];
        }
        s.edge_count=0;
    }
    xSemaphoreGive(s.lock);
    lua_createtable(L,0,10);
    lua_pushboolean(L,connected); lua_setfield(L,-2,"connected");
    lua_pushboolean(L,input_active); lua_setfield(L,-2,"input_active");
    push_keys(L,held); lua_setfield(L,-2,"held");
    push_keys(L,pressed); lua_setfield(L,-2,"pressed");
    push_keys(L,released); lua_setfield(L,-2,"released");
    const char *names[]={"lx","ly","rx","ry"};
    for(int i=0;i<4;i++) { lua_pushnumber(L,axes[i]); lua_setfield(L,-2,names[i]); }
    lua_createtable(L,count,0);
    for(unsigned i=0;i<count;i++) {
        lua_createtable(L,0,2); push_keys(L,edges[i][0]); lua_setfield(L,-2,"pressed");
        push_keys(L,edges[i][1]); lua_setfield(L,-2,"released"); lua_rawseti(L,-2,i+1);
    }
    lua_setfield(L,-2,"events"); return 1;
}
static int pad_approve(lua_State *L)
{
    /* Legacy v2 compatibility only: poll no longer exposes a confirmation request. */
    lua_pushboolean(L,0); return 1;
}
static int luaopen_gamepad(lua_State *L)
{
    const luaL_Reg methods[]={{"open",pad_open},{"poll",pad_poll},{"approve",pad_approve},{"close",pad_close},{NULL,NULL}};
    luaL_newlib(L,methods); return 1;
}
esp_err_t lua_module_gamepad_register(const char *unused)
{
    (void)unused;
    if (!s.lock) { s.lock=xSemaphoreCreateMutex(); if(!s.lock) return ESP_ERR_NO_MEM; }
    if (!s.watchdog) {
        const esp_timer_create_args_t args={.callback=watchdog,.name="gamepad_watchdog"};
        esp_err_t err=esp_timer_create(&args,&s.watchdog); if(err!=ESP_OK) return err;
        err=esp_timer_start_periodic(s.watchdog,50000);
        if(err!=ESP_OK) { esp_timer_delete(s.watchdog); s.watchdog=NULL; return err; }
    }
    esp_err_t err=cap_lua_register_exit_cleanup(cleanup); if(err!=ESP_OK) return err;
    return cap_lua_register_module("gamepad",luaopen_gamepad);
}
