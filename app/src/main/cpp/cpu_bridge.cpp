#include "cpu_bridge.h"

#include <android/log.h>

#define LOG_TAG "VxpNative"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)


// -----------------------------------------------------------------------------
// VXP register ID -> Unicorn ARM register ID
// -----------------------------------------------------------------------------

static int toUnicornRegId(int regId) {
    switch (regId) {
        case VXP_REG_R0:   return UC_ARM_REG_R0;
        case VXP_REG_R1:   return UC_ARM_REG_R1;
        case VXP_REG_R2:   return UC_ARM_REG_R2;
        case VXP_REG_R3:   return UC_ARM_REG_R3;
        case VXP_REG_R4:   return UC_ARM_REG_R4;
        case VXP_REG_R5:   return UC_ARM_REG_R5;
        case VXP_REG_R6:   return UC_ARM_REG_R6;
        case VXP_REG_R7:   return UC_ARM_REG_R7;
        case VXP_REG_R8:   return UC_ARM_REG_R8;
        case VXP_REG_R9:   return UC_ARM_REG_R9;
        case VXP_REG_R10:  return UC_ARM_REG_R10;
        case VXP_REG_R11:  return UC_ARM_REG_R11;
        case VXP_REG_R12:  return UC_ARM_REG_R12;
        case VXP_REG_SP:   return UC_ARM_REG_SP;
        case VXP_REG_LR:   return UC_ARM_REG_LR;
        case VXP_REG_PC:   return UC_ARM_REG_PC;
        case VXP_REG_CPSR: return UC_ARM_REG_CPSR;

        default:
            LOGW(
                "toUnicornRegId: unknown VxpRegisterId %d",
                regId
            );
            return -1;
    }
}


// -----------------------------------------------------------------------------
// Register read
// -----------------------------------------------------------------------------

uint32_t vxp_get_register(uc_engine* uc, int regId) {
    if (uc == nullptr) {
        return 0;
    }

    int ucReg = toUnicornRegId(regId);

    if (ucReg < 0) {
        return 0;
    }

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


// -----------------------------------------------------------------------------
// Register write
// -----------------------------------------------------------------------------

bool vxp_set_register(
        uc_engine* uc,
        int regId,
        uint32_t value) {

    if (uc == nullptr) {
        return false;
    }

    int ucReg = toUnicornRegId(regId);

    if (ucReg < 0) {
        return false;
    }

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


// -----------------------------------------------------------------------------
// ARM register diagnostics
// -----------------------------------------------------------------------------

static void logArmRegisters(uc_engine* uc) {
    if (uc == nullptr) {
        return;
    }

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

    uint32_t sp   = 0;
    uint32_t lr   = 0;
    uint32_t pc   = 0;
    uint32_t cpsr = 0;

    uc_reg_read(uc, UC_ARM_REG_R0,  &r0);
    uc_reg_read(uc, UC_ARM_REG_R1,  &r1);
    uc_reg_read(uc, UC_ARM_REG_R2,  &r2);
    uc_reg_read(uc, UC_ARM_REG_R3,  &r3);
    uc_reg_read(uc, UC_ARM_REG_R4,  &r4);
    uc_reg_read(uc, UC_ARM_REG_R5,  &r5);
    uc_reg_read(uc, UC_ARM_REG_R6,  &r6);
    uc_reg_read(uc, UC_ARM_REG_R7,  &r7);
    uc_reg_read(uc, UC_ARM_REG_R8,  &r8);
    uc_reg_read(uc, UC_ARM_REG_R9,  &r9);
    uc_reg_read(uc, UC_ARM_REG_R10, &r10);
    uc_reg_read(uc, UC_ARM_REG_R11, &r11);
    uc_reg_read(uc, UC_ARM_REG_R12, &r12);

    uc_reg_read(uc, UC_ARM_REG_SP,   &sp);
    uc_reg_read(uc, UC_ARM_REG_LR,   &lr);
    uc_reg_read(uc, UC_ARM_REG_PC,   &pc);
    uc_reg_read(uc, UC_ARM_REG_CPSR, &cpsr);

    LOGE(
        "REGS: "
        "R0=%08x "
        "R1=%08x "
        "R2=%08x "
        "R3=%08x "
        "R4=%08x "
        "R5=%08x "
        "R6=%08x "
        "R7=%08x "
        "R8=%08x "
        "R9=%08x "
        "R10=%08x "
        "R11=%08x "
        "R12=%08x "
        "SP=%08x "
        "LR=%08x "
        "PC=%08x "
        "CPSR=%08x",
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
        pc,
        cpsr
    );
}


// -----------------------------------------------------------------------------
// Invalid memory hook
// -----------------------------------------------------------------------------

static bool onInvalidMemory(
        uc_engine* uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void* user_data) {

    (void)user_data;

    uint32_t pc = 0;
    uint32_t sp = 0;
    uint32_t lr = 0;

    uc_reg_read(uc, UC_ARM_REG_PC, &pc);
    uc_reg_read(uc, UC_ARM_REG_SP, &sp);
    uc_reg_read(uc, UC_ARM_REG_LR, &lr);

    const char* access = "UNKNOWN";

    switch (type) {
        case UC_MEM_READ_UNMAPPED:
        case UC_MEM_READ_PROT:
            access = "READ";
            break;

        case UC_MEM_WRITE_UNMAPPED:
        case UC_MEM_WRITE_PROT:
            access = "WRITE";
            break;

        case UC_MEM_FETCH_UNMAPPED:
        case UC_MEM_FETCH_PROT:
            access = "FETCH";
            break;

        default:
            break;
    }

    LOGE(
        "INVALID MEMORY: "
        "type=%s "
        "address=0x%llx "
        "size=%d "
        "value=0x%llx "
        "PC=0x%08x "
        "SP=0x%08x "
        "LR=0x%08x",
        access,
        (unsigned long long)address,
        size,
        (unsigned long long)value,
        pc,
        sp,
        lr
    );


    // Dump 32 bytes around the faulting PC.
uint64_t dumpStart = (pc >= 0x20) ? (uint64_t)(pc - 0x20) : 0;
uint8_t beforeCode[64] = {};

uc_err dumpErr = uc_mem_read(
    uc,
    dumpStart,
    beforeCode,
    sizeof(beforeCode)
);

if (dumpErr == UC_ERR_OK) {
    LOGE(
        "CODE AROUND PC=0x%08x:",
        pc
    );

    for (int i = 0; i < 64; i += 4) {
        uint32_t word =
            (uint32_t)beforeCode[i] |
            ((uint32_t)beforeCode[i + 1] << 8) |
            ((uint32_t)beforeCode[i + 2] << 16) |
            ((uint32_t)beforeCode[i + 3] << 24);

        LOGE(
            "  0x%08llx : %08x",
            (unsigned long long)(dumpStart + i),
            word
        );
    }
}
    // ---------------------------------------------------------
    // NEW: Read the instruction bytes at the faulting PC.
    // ---------------------------------------------------------

    uint8_t code[8] = {};

    uc_err readErr = uc_mem_read(
        uc,
        (uint64_t)pc,
        code,
        sizeof(code)
    );

    if (readErr == UC_ERR_OK) {

        LOGE(
            "FAULT INSTRUCTION BYTES: "
            "%02x %02x %02x %02x %02x %02x %02x %02x",
            code[0],
            code[1],
            code[2],
            code[3],
            code[4],
            code[5],
            code[6],
            code[7]
        );

    } else {

        LOGE(
            "Could not read instruction bytes at PC=0x%08x: %s",
            pc,
            uc_strerror(readErr)
        );
    }

    // ---------------------------------------------------------
    // Register dump
    // ---------------------------------------------------------

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
    uint32_t cpsr = 0;

    uc_reg_read(uc, UC_ARM_REG_R0,  &r0);
    uc_reg_read(uc, UC_ARM_REG_R1,  &r1);
    uc_reg_read(uc, UC_ARM_REG_R2,  &r2);
    uc_reg_read(uc, UC_ARM_REG_R3,  &r3);
    uc_reg_read(uc, UC_ARM_REG_R4,  &r4);
    uc_reg_read(uc, UC_ARM_REG_R5,  &r5);
    uc_reg_read(uc, UC_ARM_REG_R6,  &r6);
    uc_reg_read(uc, UC_ARM_REG_R7,  &r7);
    uc_reg_read(uc, UC_ARM_REG_R8,  &r8);
    uc_reg_read(uc, UC_ARM_REG_R9,  &r9);
    uc_reg_read(uc, UC_ARM_REG_R10, &r10);
    uc_reg_read(uc, UC_ARM_REG_R11, &r11);
    uc_reg_read(uc, UC_ARM_REG_R12, &r12);
    uc_reg_read(uc, UC_ARM_REG_CPSR, &cpsr);

    LOGE(
        "REGS: "
        "R0=%08x R1=%08x R2=%08x R3=%08x "
        "R4=%08x R5=%08x R6=%08x R7=%08x "
        "R8=%08x R9=%08x R10=%08x R11=%08x R12=%08x "
        "SP=%08x LR=%08x PC=%08x CPSR=%08x",
        r0, r1, r2, r3,
        r4, r5, r6, r7,
        r8, r9, r10, r11, r12,
        sp, lr, pc, cpsr
    );

    // Do NOT map address 0.
    // Let Unicorn stop execution.
    return false;
}


// -----------------------------------------------------------------------------
// Execute VXP
// -----------------------------------------------------------------------------

uc_err vxp_run(
        uc_engine* uc,
        uint64_t startAddress,
        uint64_t endAddress,
        uint64_t timeoutMicros,
        size_t maxInstructions) {

    if (uc == nullptr) {
        LOGE("vxp_run: uc == nullptr");
        return UC_ERR_HANDLE;
    }

    // -------------------------------------------------------------------------
    // Install invalid-memory diagnostic hook.
    // -------------------------------------------------------------------------

    uc_hook invalidHook = 0;

    uc_err hookErr = uc_hook_add(
        uc,
        &invalidHook,
        UC_HOOK_MEM_INVALID,
        (void*) onInvalidMemory,
        nullptr,
        1,
        0
    );

    if (hookErr != UC_ERR_OK) {
        LOGE(
            "Failed to install invalid-memory hook: %s",
            uc_strerror(hookErr)
        );

        return hookErr;
    }

    LOGI(
        "Starting VXP: start=0x%llx end=0x%llx",
        (unsigned long long) startAddress,
        (unsigned long long) endAddress
    );

    // -------------------------------------------------------------------------
    // Start emulation.
    // -------------------------------------------------------------------------

    uc_err err = uc_emu_start(
        uc,
        startAddress,
        endAddress,
        timeoutMicros,
        maxInstructions
    );

    // -------------------------------------------------------------------------
    // Always remove the hook after execution.
    //
    // Otherwise every vxp_run() call would install another hook and
    // eventually produce duplicate diagnostics.
    // -------------------------------------------------------------------------

    uc_err deleteErr = uc_hook_del(
        uc,
        invalidHook
    );

    if (deleteErr != UC_ERR_OK) {
        LOGW(
            "Failed to remove invalid-memory hook: %s",
            uc_strerror(deleteErr)
        );
    }

    // -------------------------------------------------------------------------
    // Report execution error.
    // -------------------------------------------------------------------------

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_start("
            "start=0x%llx, "
            "end=0x%llx"
            ") failed: %s",
            (unsigned long long) startAddress,
            (unsigned long long) endAddress,
            uc_strerror(err)
        );
    }

    return err;
}


// -----------------------------------------------------------------------------
// Execute exactly one instruction
// -----------------------------------------------------------------------------

uc_err vxp_step(uc_engine* uc) {
    if (uc == nullptr) {
        LOGE("vxp_step: uc == nullptr");
        return UC_ERR_HANDLE;
    }

    // IMPORTANT:
    //
    // uc_emu_start(begin, ...) sets the guest PC to `begin`.
    // Therefore we must first read the CURRENT PC and use that value
    // as the begin address.
    //

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
        "vxp_step: executing one instruction at PC=0x%08x",
        currentPc
    );

    // count = 1 guarantees that at most one instruction is executed.
    //
    // We intentionally do not use until=0 because address 0 can be a
    // legitimate guest address in some VXP files.
    //
    // 0xFFFFFFFF is used only as an execution boundary; count=1 is what
    // actually limits this call to one instruction.
    constexpr uint64_t NO_END_ADDRESS_LIMIT = 0xFFFFFFFFULL;

    err = uc_emu_start(
        uc,
        currentPc,
        NO_END_ADDRESS_LIMIT,
        0,      // timeout
        1       // exactly one instruction
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "vxp_step failed at PC=0x%08x: %s",
            currentPc,
            uc_strerror(err)
        );
    }

    return err;
}


// -----------------------------------------------------------------------------
// Stop emulation
// -----------------------------------------------------------------------------

void vxp_stop(uc_engine* uc) {
    if (uc == nullptr) {
        return;
    }

    uc_err err = uc_emu_stop(uc);

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_emu_stop failed: %s",
            uc_strerror(err)
        );
    }
}
