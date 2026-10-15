"""Native execution of the pinned SDK workaround, without device access."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def test_usb_dma_shutdown_only_for_clocked_device_mode(tmp_path):
    patch = (ROOT / "tools/patches/idf-7b9cc1ac-s31-usb-restart.patch").read_text()
    added = "\n".join(line[1:] for line in patch.splitlines()
                      if line.startswith("+") and not line.startswith("+++"))
    body = added[added.index("    // USB has its own DMA master"):]
    source = r'''
#include <assert.h>
#include <stdint.h>
#include <stdbool.h>
typedef struct {
    struct { unsigned dmaen; } gahbcfg_reg;
    struct { unsigned curmod; } gintsts_reg;
    struct { unsigned sftdiscon; } dctl_reg;
} usb_dwc_dev_t;
static usb_dwc_dev_t hw;
static unsigned clocks, phase, polls;
#define HP_SYS_CLKRST_REG_USB_OTGHS_APB_CLK_EN 1u
#define HP_SYS_CLKRST_REG_USB_OTGHS_SYS_CLK_EN 2u
#define HP_SYS_CLKRST_USB_OTGHS_CTRL0_REG 0x205870acu
#define REG_READ(r) ((void)(r), clocks)
#define USB_DWC_LL_GET_HW(n) ((void)(n), &hw)
static void usb_dwc_ll_gahbcfg_dis_global_intr(usb_dwc_dev_t *u) {
    assert(u == &hw && clocks == 3 && u->dctl_reg.sftdiscon);
    assert(phase == 0);
    phase = 1;
}
static bool usb_dwc_ll_grstctl_is_ahb_idle(usb_dwc_dev_t *u) {
    assert(u == &hw && (phase == 1 || phase == 2));
    return (++polls % 2) == 0;
}
static void usb_dwc_ll_grstctl_core_soft_reset(usb_dwc_dev_t *u) {
    assert(u == &hw && phase == 1 && polls == 2);
    phase = 2;
    u->dctl_reg.sftdiscon = 0; /* Driver must leave us disconnected again. */
}
static void usb_dwc_ll_gahbcfg_en_slave_mode(usb_dwc_dev_t *u) {
    assert(u == &hw && phase == 2 && polls == 4);
    phase = 3;
    u->gahbcfg_reg.dmaen = 0;
}
static void reset_usb(void) {
''' + body + r'''
}
int main(void) {
    for (unsigned c = 0; c < 4; c++) {
        for (unsigned dma = 0; dma < 2; dma++) {
            for (unsigned host = 0; host < 2; host++) {
                clocks = c; phase = polls = 0;
                hw = (usb_dwc_dev_t){0};
                hw.gahbcfg_reg.dmaen = dma;
                hw.gintsts_reg.curmod = host;
                reset_usb();
                if (c == 3 && dma && !host) {
                    assert(phase == 3 && hw.dctl_reg.sftdiscon == 1);
                    assert(hw.gahbcfg_reg.dmaen == 0);
                } else {
                    assert(phase == 0 && polls == 0);
                    assert(hw.gahbcfg_reg.dmaen == dma);
                }
            }
        }
    }
}
'''
    compiler = shutil.which("clang") or shutil.which("gcc")
    assert compiler, "Native C compiler required for the USB restart regression"
    c_file = tmp_path / "usb_restart.c"
    binary = tmp_path / "usb_restart.exe"
    c_file.write_text(source)
    subprocess.run([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror",
                    str(c_file), "-o", str(binary)], check=True, capture_output=True)
    subprocess.run([str(binary)], check=True, timeout=5)
