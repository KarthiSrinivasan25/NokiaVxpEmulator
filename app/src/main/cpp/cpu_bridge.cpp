#include "cpu_bridge.h"

#include <unicorn/unicorn.h>

#include <android/log.h>

#include <cstdint>
#include <cstdio>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// ============================================================
// VXP register -> Unicorn register
// ============================================================

static int toUnicornRegId(int regId)
{
    switch (regId) {

        case VXP_REG_R0:
            return UC_ARM_REG_R0;

        case VXP_REG_R1:
            return UC_ARM_REG_R1;

        case VXP_REG_R2:
            return UC_ARM_REG_R2;

        case VXP_REG_R3:
            return UC_ARM_REG_R3;

        case VXP_REG_R4:
            return UC_ARM_REG_R4;

        case VXP_REG_R5:
            return UC_ARM_REG_R5;

        case VXP_REG_R6:
            return UC_ARM_REG_R6;

        case VXP_REG_R7:
            return UC_ARM_REG_R7;

        case VXP_REG_R8:
            return UC_ARM_REG_R8;

        case VXP_REG_R9:
            return UC_ARM_REG_R9;

        case VXP_REG_R10:
            return UC_ARM_REG_R10;

        case VXP_REG_R11:
            return UC_ARM_REG_R11;

        case VXP_REG_R12:
            return UC_ARM_REG_R12;

        case VXP_REG_SP:
            return UC_ARM_REG_SP;

        case VXP_REG_LR:
            return UC_ARM_REG_LR;

        case VXP_REG_PC:
            return UC_ARM_REG_PC;

        case VXP_REG_CPSR:
            return UC_ARM_REG_CPSR;

        default:
            LOGW(
                "toUnicornRegId: unknown VxpRegisterId %d",
                regId
            );

            return -1;
    }
}


// ============================================================
// Get register
// ============================================================

uint32_t vxp_get_register(
        uc_engine *uc,
        int regId)
{
    if (uc == nullptr)
        return 0;

    int ucReg = toUnicornRegId(regId);

    if (ucReg < 0)
        return 0;

    uint32_t value = 0;

    uc_err err = uc_reg_read(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_reg_read(regId=%d) failed: %s",
            regId,
            uc_strerror(err)
        );

        return 0;
    }

    return value;
}


// ============================================================
// Set register
// ============================================================

bool vxp_set_register(
        uc_engine *uc,
        int regId,
        uint32_t value)
{
    if (uc == nullptr)
        return false;

    int ucReg = toUnicornRegId(regId);

    if (ucReg < 0)
        return false;

    uc_err err = uc_reg_write(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK) {

        LOGE(
            "uc_reg_write(regId=%d, value=0x%08x) failed: %s",
            regId,
            value,
            uc_strerror(err)
        );

        return false;
    }

    return true;
}


// ============================================================
// Instruction trace callback
//
// This is the important debugging part.
//
// We print instructions around:
//     0x8000 -> 0x8100
//
// This should tell us exactly what executes at 0x8058.
// ============================================================

static void trace_code(
        uc_engine *uc,
        uint64_t address,
        uint32_t size,
        void *user_data)
{
    (void)user_data;

    // Trace only the beginning of the VXP.
    if (address < 0x8000 ||
        address >= 0x8100) {
        return;
    }

    uint32_t r0 = 0;
    uint32_t r1 = 0;
    uint32_t r2 = 0;
    uint32_t r3 = 0;

    uint32_t r4 = 0;
    uint32_t r5 = 0;
    uint32_t r6 = 0;
    uint32_t r7 = 0;

    uint32_t r8 = 0;
    uint32_t r9 = 0;
    uint32_t r10 = 0;
    uint32_t r11 = 0;
    uint32_t r12 = 0;

    uint32_t sp = 0;
    uint32_t lr = 0;
    uint32_t pc = 0;
    uint32_t cpsr = 0;


    // --------------------------------------------------------
    // Read registers
    // --------------------------------------------------------

    uc_reg_read(
        uc,
        UC_ARM_REG_R0,
        &r0
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R1,
        &r1
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R2,
        &r2
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R3,
        &r3
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R4,
        &r4
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R5,
        &r5
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R6,
        &r6
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R7,
        &r7
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R8,
        &r8
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R9,
        &r9
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R10,
        &r10
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R11,
        &r11
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_R12,
        &r12
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_SP,
        &sp
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_LR,
        &lr
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &pc
    );

    uc_reg_read(
        uc,
        UC_ARM_REG_CPSR,
        &cpsr
    );


    // --------------------------------------------------------
    // Read instruction bytes
    // --------------------------------------------------------

    uint8_t bytes[8] = {};

    uint32_t readSize = size;

    if (readSize > sizeof(bytes))
        readSize = sizeof(bytes);


    uc_err readErr = uc_mem_read(
        uc,
        address,
        bytes,
        readSize
    );


    // --------------------------------------------------------
    // Format bytes
    // --------------------------------------------------------

    char hex[64] = {};
    char *p = hex;

    if (readErr == UC_ERR_OK) {

        for (uint32_t i = 0;
             i < readSize;
             i++) {

            int written = snprintf(
                p,
                sizeof(hex) - static_cast<size_t>(p - hex),
                "%02X ",
                bytes[i]
            );

            if (written <= 0)
                break;

            p += written;
        }
    }
    else {

        snprintf(
            hex,
            sizeof(hex),
            "read-error=%s",
            uc_strerror(readErr)
        );
    }


    // --------------------------------------------------------
    // Print execution
    // --------------------------------------------------------

    LOGI(
        "EXEC PC=0x%08llX SIZE=%u "
        "BYTES=[%s]",
        (unsigned long long)address,
        size,
        hex
    );


    // --------------------------------------------------------
    // Print registers
    // --------------------------------------------------------

    LOGI(
        "REG "
        "R0=%08X "
        "R1=%08X "
        "R2=%08X "
        "R3=%08X "
        "R4=%08X "
        "R5=%08X "
        "R6=%08X "
        "R7=%08X",
        r0,
        r1,
        r2,
        r3,
        r4,
        r5,
        r6,
        r7
    );


    LOGI(
        "REG "
        "R8=%08X "
        "R9=%08X "
        "R10=%08X "
        "R11=%08X "
        "R12=%08X "
        "SP=%08X "
        "LR=%08X "
        "CPSR=%08X",
        r8,
        r9,
        r10,
        r11,
        r12,
        sp,
        lr,
        cpsr
    );


    // --------------------------------------------------------
    // Special warning for the faulting area
    // --------------------------------------------------------

    if (address >= 0x8050 &&
        address <= 0x8060) {

        LOGW(
            "NEAR FAULT AREA: PC=0x%08llX "
            "R0=%08X R1=%08X R2=%08X R3=%08X "
            "R4=%08X R5=%08X R6=%08X R7=%08X "
            "SP=%08X LR=%08X",
            (unsigned long long)address,
            r0,
            r1,
            r2,
            r3,
            r4,
            r5,
            r6,
            r7,
            sp,
            lr
        );
    }
}


// ============================================================
// Install instruction trace
// ============================================================

void install_instruction_trace(
        uc_engine *uc)
{
    if (uc == nullptr) {

        LOGE(
            "install_instruction_trace: uc == nullptr"
        );

        return;
    }


    uc_hook trace;


    /*
     * IMPORTANT:
     *
     * Your Unicorn header defines callback as void*.
     *
     * Therefore we explicitly cast trace_code.
     */
    uc_err err = uc_hook_add(
        uc,
        &trace,
        UC_HOOK_CODE,
        reinterpret_cast<void *>(trace_code),
        nullptr,
        0,
        0
    );


    if (err != UC_ERR_OK) {

        LOGE(
            "Instruction trace hook failed: %s",
            uc_strerror(err)
        );

        return;
    }


    LOGI(
        "Instruction trace hook installed"
    );
}


// ============================================================
// Run
// ============================================================

uc_err vxp_run(
        uc_engine *uc,
        uint64_t startAddress,
        uint64_t endAddress,
        uint64_t timeoutMicros,
        size_t maxInstructions)
{
    if (uc == nullptr)
        return UC_ERR_HANDLE;


    LOGI(
        "Starting Unicorn: start=0x%llx end=0x%llx",
        (unsigned long long)startAddress,
        (unsigned long long)endAddress
    );


    uc_err err = uc_emu_start(
        uc,
        startAddress,
        endAddress,
        timeoutMicros,
        maxInstructions
    );


    if (err != UC_ERR_OK) {

        uint32_t pc = 0;
        uint32_t sp = 0;
        uint32_t lr = 0;

        uc_reg_read(
            uc,
            UC_ARM_REG_PC,
            &pc
        );

        uc_reg_read(
            uc,
            UC_ARM_REG_SP,
            &sp
        );

        uc_reg_read(
            uc,
            UC_ARM_REG_LR,
            &lr
        );


        LOGE(
            "uc_emu_start(start=0x%llx, end=0x%llx) "
            "failed: %s "
            "PC=0x%08x SP=0x%08x LR=0x%08x",
            (unsigned long long)startAddress,
            (unsigned long long)endAddress,
            uc_strerror(err),
            pc,
            sp,
            lr
        );
    }


    return err;
}


// ============================================================
// Single step
// ============================================================

uc_err vxp_step(
        uc_engine *uc)
{
    if (uc == nullptr)
        return UC_ERR_HANDLE;


    uint32_t currentPc = 0;


    uc_err err = uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &currentPc
    );


    if (err != UC_ERR_OK) {

        LOGE(
            "vxp_step: failed to read current PC: %s",
            uc_strerror(err)
        );

        return err;
    }


    LOGI(
        "vxp_step: PC=0x%08x",
        currentPc
    );


    /*
     * count = 1 means execute exactly one instruction.
     *
     * Do NOT use until=0 because address 0 can be a valid
     * guest address for some VXP files.
     */

    constexpr uint64_t NO_END_ADDRESS_LIMIT =
        0xFFFFFFFFULL;


    err = uc_emu_start(
        uc,
        currentPc,
        NO_END_ADDRESS_LIMIT,
        0,
        1
    );


    if (err != UC_ERR_OK) {

        LOGE(
            "vxp_step failed: %s",
            uc_strerror(err)
        );
    }


    return err;
}


// ============================================================
// Stop
// ============================================================

void vxp_stop(
        uc_engine *uc)
{
    if (uc == nullptr)
        return;


    uc_err err = uc_emu_stop(
        uc
    );


    if (err != UC_ERR_OK) {

        LOGE(
            "uc_emu_stop failed: %s",
            uc_strerror(err)
        );
    }
}