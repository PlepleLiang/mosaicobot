#pragma once
#include "FreeRTOS.h"
unsigned uxQueueSpacesAvailable(QueueHandle_t queue);
int xQueueSend(QueueHandle_t queue, const void *item, unsigned timeout);
