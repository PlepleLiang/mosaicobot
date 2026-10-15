#include "http_server_priv.h"
#include "lua_module_gamepad.h"
#include <stdlib.h>
#include <string.h>
#include "lwip/sockets.h"

#define GAMEPAD_FRAME_MAX 512
/* IDF 6.2 skips the URI handler during upgrade: reserve the request before 101. */
static esp_err_t gamepad_pre_handshake(httpd_req_t *req)
{
    int fd=httpd_req_to_sockfd(req);
    int nodelay=1;
    /* WS header/payload are separate small writes; never wait for Nagle/delayed ACK. */
    if (setsockopt(fd,IPPROTO_TCP,TCP_NODELAY,&nodelay,sizeof(nodelay))!=0) return ESP_FAIL;
    if (gamepad_connect(fd)) return ESP_OK;
    httpd_resp_set_status(req,"409 Conflict");
    httpd_resp_send(req,"Gamepad unavailable or controller connected",HTTPD_RESP_USE_STRLEN);
    return ESP_FAIL; /* A rejected request must not upgrade to WebSocket. */
}

static esp_err_t gamepad_ws(httpd_req_t *req)
{
    int fd=httpd_req_to_sockfd(req);
    if(req->method==HTTP_GET) {
        return httpd_resp_send_err(req,HTTPD_400_BAD_REQUEST,"WebSocket upgrade required");
    }
    httpd_ws_frame_t frame={0};
    esp_err_t err=httpd_ws_recv_frame(req,&frame,0);
    if(err!=ESP_OK) { gamepad_disconnect(fd); return err; }
    if(frame.type==HTTPD_WS_TYPE_CLOSE) { gamepad_disconnect(fd); return ESP_OK; }
    if(frame.type!=HTTPD_WS_TYPE_TEXT || frame.len>GAMEPAD_FRAME_MAX || !frame.final) {
        gamepad_disconnect(fd); return ESP_FAIL;
    }
    char *body=calloc(1,frame.len+1); if(!body) { gamepad_disconnect(fd); return ESP_ERR_NO_MEM; }
    frame.payload=(uint8_t *)body;
    err=httpd_ws_recv_frame(req,&frame,frame.len);
    if(err!=ESP_OK) { free(body); gamepad_disconnect(fd); return err; }
    const char *end=NULL;
    cJSON *packet=cJSON_ParseWithLengthOpts(body,frame.len+1,&end,true);
    cJSON *reply=gamepad_exchange(fd,packet);
    cJSON_Delete(packet); free(body);
    if(!reply) { gamepad_disconnect(fd); return ESP_ERR_NO_MEM; }
    char *text=cJSON_PrintUnformatted(reply); cJSON_Delete(reply);
    if(!text) { gamepad_disconnect(fd); return ESP_ERR_NO_MEM; }
    httpd_ws_frame_t response={.type=HTTPD_WS_TYPE_TEXT,.payload=(uint8_t *)text,.len=strlen(text)};
    err=httpd_ws_send_frame(req,&response); free(text);
    if(err!=ESP_OK) gamepad_disconnect(fd);
    return err;
}
esp_err_t http_server_register_gamepad_routes(httpd_handle_t server)
{
    const httpd_uri_t route={.uri="/ws/gamepad",.method=HTTP_GET,.handler=gamepad_ws,
        .is_websocket=true,.ws_pre_handshake_cb=gamepad_pre_handshake};
    return httpd_register_uri_handler(server,&route);
}
