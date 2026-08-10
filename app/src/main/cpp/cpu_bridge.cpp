#include "cpu_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// ============================================================
// Convert VXP register ID -> Unicorn register ID
// ============================================================

static int toUnicornRegId(int regId) {
    switch (regId) {

        case VXP_REG_R0:  return UC_ARM_REG_R0;
        case VXP_REG_R1:  return UC_ARM_REG_R1;
        case VXP_REG_R2:  return UC_ARM_REG_R2;
        case VXP_REG_R3:  return UC_ARM_REG_R3;
        case VXP_REG_R4:  return UC_ARM_REG_R4;
        case VXP_REG_R5:  return UC_ARM_REG_R5;
        case VXP_REG_R6:  return UC_ARM_REG_R6;
        case VXP_REG_R7:  return UC_ARM_REG_R7;
        case VXP_REG_R8:  return UC_ARM_REG_R8;
        case VXP_REG_R9:  return UC_ARM_REG_R9;
        case VXP_REG_R10: return UC_ARM_REG_R10;
        case VXP_REG_R11: return UC_ARM_REG_R11;
        case VXP_REG_R12: return UC_ARM_REG_R12;

        case VXP_REG_SP:  return UC_ARM_REG_SP;
        case VXP_REG_LR:  return UC_ARM_REG_LR;
        case VXP_REG_PC:  return UC_ARM_REG_PC;
        case VXP_REG_CPSR:return UC_ARM_REG_CPSR;

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

uint32_t vxp_get_register(uc_engine* uc, int regId) {

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
    uc_engine* uc,
    int regId,
    uint32_t value
) {

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
            "uc_reg_write(regId=%d, value=0x%x) failed: %s",
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
// ============================================================

static void vxp_trace_code(
    uc_engine* uc,
    uint64_t address,
    uint32_t size,
    void* user_data
) {

    // We are interested in the startup area around 0x8000.
    if (address < 0x8000 || address >= 0x8100)
        return;

    uint32_t r0  = 0;
    uint32_t r1  = 0;
    uint32_t r2  = 0;
    uint32_t r3  = 0;
    uint32_t r4  = 0;
    uint32_t r5  = 0;
    uint32_t r6  = 0;
    uint32_t r7  = 0;
    uint32_t r8  = 0;
    uint32_t r9  = 0;
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
    // Log instruction + registers
    // --------------------------------------------------------

    __android_log_print(
        ANDROID_LOG_ERROR,
        "VxpTrace",

        "PC=0x%08llx SIZE=%u "
        "R0=%08x R1=%08x R2=%08x R3=%08x "
        "R4=%08x R5=%08x R6=%08x R7=%08x "
        "R8=%08x R9=%08x R10=%08x R11=%08x R12=%08x "
        "SP=%08x LR=%08x CPSR=%08x",

        (unsigned long long)address,
        size,

        r0,
        r1,
        r2,
        r3,

        r4,
        r5,
        r6,
        r7,

        r8,
        r9,
        r10,
        r11,
        r12,

        sp,
        lr,
        cpsr
    );
}


// ============================================================
// Memory fault callback
// ============================================================

static bool vxp_trace_memory(
    uc_engine* uc,
    uc_mem_type type,
    uint64_t address,
    int size,
    int64_t value,
    void* user_data
) {

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


    const char* operation = "UNKNOWN";

    switch (type) {

        case UC_MEM_READ_UNMAPPED:
            operation = "READ_UNMAPPED";
            break;

        case UC_MEM_WRITE_UNMAPPED:
            operation = "WRITE_UNMAPPED";
            break;

        case UC_MEM_FETCH_UNMAPPED:
            operation = "FETCH_UNMAPPED";
            break;

        case UC_MEM_READ_PROT:
            operation = "READ_PROT";
            break;

        case UC_MEM_WRITE_PROT:
            operation = "WRITE_PROT";
            break;

        case UC_MEM_FETCH_PROT:
            operation = "FETCH_PROT";
            break;

        default:
            break;
    }


    LOGE(
        "MEMORY FAULT: %s "
        "address=0x%llx size=%d value=0x%llx "
        "PC=0x%08x SP=0x%08x LR=0x%08x",

        operation,

        (unsigned long long)address,
        size,
        (unsigned long long)value,

        pc,
        sp,
        lr
    );


    // Returning false tells Unicorn to stop on this fault.
    return false;
}


// ============================================================
// Run VXP
// ============================================================

uc_err vxp_run(
    uc_engine* uc,
    uint64_t startAddress,
    uint64_t endAddress,
    uint64_t timeoutMicros,
    size_t maxInstructions
) {

    if (uc == nullptr)
        return UC_ERR_HANDLE;


    // ========================================================
    // Install instruction trace
    // ========================================================

    uc_hook traceHook;

    uc_err hookErr = uc_hook_add(
        uc,
        &traceHook,

        UC_HOOK_CODE,

        (void*)vxp_trace_code,

        nullptr,

        0,
        0
    );


    if (hookErr != UC_ERR_OK) {

        LOGE(
            "Failed to install instruction trace hook: %s",
            uc_strerror(hookErr)
        );

    } else {

        LOGI(
            "Instruction trace hook installed"
        );
    }


    // ========================================================
    // Install memory fault trace
    // ========================================================

    uc_hook memoryHook;

    uc_err memoryHookErr = uc_hook_add(
        uc,
        &memoryHook,

        UC_HOOK_MEM_READ_UNMAPPED |
        UC_HOOK_MEM_WRITE_UNMAPPED |
        UC_HOOK_MEM_FETCH_UNMAPPED |
        UC_HOOK_MEM_READ_PROT |
        UC_HOOK_MEM_WRITE_PROT |
        UC_HOOK_MEM_FETCH_PROT,

        (void*)vxp_trace_memory,

        nullptr,

        1,
        0
    );


    if (memoryHookErr != UC_ERR_OK) {

        LOGE(
            "Failed to install memory fault hook: %s",
            uc_strerror(memoryHookErr)
        );

    } else {

        LOGI(
            "Memory fault trace hook installed"
        );
    }


    // ========================================================
    // Start Unicorn
    // ========================================================

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
            "uc_emu_start(start=0x%llx, end=0x%llx) failed: %s "
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
    uc_engine* uc
) {

    if (uc == nullptr)
        return UC_ERR_HANDLE;


    // --------------------------------------------------------
    // Read current PC
    // --------------------------------------------------------

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


    // --------------------------------------------------------
    // Single instruction
    // --------------------------------------------------------

    constexpr uint64_t NO_END_ADDRESS_LIMIT = 0xFFFFFFFFULL;


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
    uc_engine* uc
) {

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