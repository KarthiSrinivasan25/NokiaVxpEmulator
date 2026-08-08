
#include "cpu_bridge.h"

#include <android/log.h>
#include <cstdint>
#include <cstddef>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)


// =============================================================================
// VXP register ID -> Unicorn ARM register ID
// =============================================================================

static int toUnicornRegId(int regId)
{
    switch (regId)
    {
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
                "toUnicornRegId: unknown VxpRegisterId=%d",
                regId
            );
            return -1;
    }
}


// =============================================================================
// Register read
// =============================================================================

uint32_t vxp_get_register(
    uc_engine* uc,
    int regId)
{
    if (uc == nullptr)
        return 0;

    const int ucReg = toUnicornRegId(regId);

    if (ucReg < 0)
        return 0;

    uint32_t value = 0;

    const uc_err err = uc_reg_read(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK)
    {
        LOGE(
            "uc_reg_read(regId=%d) failed: %s",
            regId,
            uc_strerror(err)
        );

        return 0;
    }

    return value;
}


// =============================================================================
// Register write
// =============================================================================

bool vxp_set_register(
    uc_engine* uc,
    int regId,
    uint32_t value)
{
    if (uc == nullptr)
        return false;

    const int ucReg = toUnicornRegId(regId);

    if (ucReg < 0)
        return false;

    const uc_err err = uc_reg_write(
        uc,
        ucReg,
        &value
    );

    if (err != UC_ERR_OK)
    {
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


// =============================================================================
// Read one ARM register safely
// =============================================================================

static uint32_t readArmReg(
    uc_engine* uc,
    int reg)
{
    uint32_t value = 0;

    if (uc == nullptr)
        return 0;

    if (uc_reg_read(uc, reg, &value) != UC_ERR_OK)
        return 0;

    return value;
}


// =============================================================================
// Register diagnostics
// =============================================================================

static void logArmRegisters(
    uc_engine* uc)
{
    if (uc == nullptr)
        return;

    const uint32_t r0 =
        readArmReg(uc, UC_ARM_REG_R0);

    const uint32_t r1 =
        readArmReg(uc, UC_ARM_REG_R1);

    const uint32_t r2 =
        readArmReg(uc, UC_ARM_REG_R2);

    const uint32_t r3 =
        readArmReg(uc, UC_ARM_REG_R3);

    const uint32_t r4 =
        readArmReg(uc, UC_ARM_REG_R4);

    const uint32_t r5 =
        readArmReg(uc, UC_ARM_REG_R5);

    const uint32_t r6 =
        readArmReg(uc, UC_ARM_REG_R6);

    const uint32_t r7 =
        readArmReg(uc, UC_ARM_REG_R7);

    const uint32_t r8 =
        readArmReg(uc, UC_ARM_REG_R8);

    const uint32_t r9 =
        readArmReg(uc, UC_ARM_REG_R9);

    const uint32_t r10 =
        readArmReg(uc, UC_ARM_REG_R10);

    const uint32_t r11 =
        readArmReg(uc, UC_ARM_REG_R11);

    const uint32_t r12 =
        readArmReg(uc, UC_ARM_REG_R12);

    const uint32_t sp =
        readArmReg(uc, UC_ARM_REG_SP);

    const uint32_t lr =
        readArmReg(uc, UC_ARM_REG_LR);

    const uint32_t pc =
        readArmReg(uc, UC_ARM_REG_PC);

    const uint32_t cpsr =
        readArmReg(uc, UC_ARM_REG_CPSR);

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


// =============================================================================
// Dump code around PC
// =============================================================================

static void dumpCodeAroundPC(
    uc_engine* uc,
    uint32_t pc)
{
    if (uc == nullptr)
        return;

    constexpr uint64_t DUMP_SIZE = 64;

    const uint64_t dumpStart =
        (pc >= 0x40)
            ? static_cast<uint64_t>(pc - 0x40)
            : 0;

    uint8_t code[DUMP_SIZE] = {};

    const uc_err err = uc_mem_read(
        uc,
        dumpStart,
        code,
        sizeof(code)
    );

    if (err != UC_ERR_OK)
    {
        LOGE(
            "Could not dump code around PC=0x%08x: %s",
            pc,
            uc_strerror(err)
        );

        return;
    }

    LOGE(
        "CODE AROUND PC=0x%08x:",
        pc
    );

    for (size_t i = 0; i < DUMP_SIZE; i += 4)
    {
        const uint32_t word =
            static_cast<uint32_t>(code[i]) |
            (static_cast<uint32_t>(code[i + 1]) << 8) |
            (static_cast<uint32_t>(code[i + 2]) << 16) |
            (static_cast<uint32_t>(code[i + 3]) << 24);

        LOGE(
            "  0x%08llx : %08x",
            static_cast<unsigned long long>(dumpStart + i),
            word
        );
    }
}


// =============================================================================
// Invalid memory hook
//
// IMPORTANT:
// Returning false means:
//     DO NOT recover the access.
//     Abort emulation.
//
// We intentionally do NOT map address 0 here.
// Mapping address 0 is a loader/memory-layout decision, not a CPU bridge
// decision.
// =============================================================================

static bool onInvalidMemory(
    uc_engine* uc,
    uc_mem_type type,
    uint64_t address,
    int size,
    int64_t value,
    void* userData)
{
    (void)userData;

    if (uc == nullptr)
        return false;

    const uint32_t pc =
        readArmReg(uc, UC_ARM_REG_PC);

    const uint32_t sp =
        readArmReg(uc, UC_ARM_REG_SP);

    const uint32_t lr =
        readArmReg(uc, UC_ARM_REG_LR);

    const uint32_t cpsr =
        readArmReg(uc, UC_ARM_REG_CPSR);

    const char* access = "UNKNOWN";

    switch (type)
    {
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
        "MEMORY FAULT: "
        "%s "
        "address=0x%llx "
        "size=%d "
        "value=0x%llx "
        "PC=0x%08x "
        "SP=0x%08x "
        "LR=0x%08x "
        "CPSR=0x%08x",
        access,
        static_cast<unsigned long long>(address),
        size,
        static_cast<unsigned long long>(value),
        pc,
        sp,
        lr,
        cpsr
    );

    // Extra diagnostics.
    dumpCodeAroundPC(uc, pc);

    // Full register state.
    logArmRegisters(uc);

    // DO NOT silently recover.
    //
    // If we return true here, Unicorn expects the callback to have
    // resolved the memory problem (for example by mapping memory).
    //
    // Since we do not know the correct VXP memory layout here,
    // aborting is safer than inventing a mapping.
    return false;
}


// =============================================================================
// Run VXP
// =============================================================================

uc_err vxp_run(
    uc_engine* uc,
    uint64_t startAddress,
    uint64_t endAddress,
    uint64_t timeoutMicros,
    size_t maxInstructions)
{
    if (uc == nullptr)
    {
        LOGE("vxp_run: uc == nullptr");
        return UC_ERR_HANDLE;
    }

    uc_hook invalidHook = 0;

    const uc_err hookErr = uc_hook_add(
        uc,
        &invalidHook,
        UC_HOOK_MEM_INVALID,
        reinterpret_cast<void*>(onInvalidMemory),
        nullptr,
        1,
        0
    );

    if (hookErr != UC_ERR_OK)
    {
        LOGE(
            "Failed to install invalid-memory hook: %s",
            uc_strerror(hookErr)
        );

        return hookErr;
    }

    LOGI(
        "Starting VXP: "
        "start=0x%llx "
        "end=0x%llx "
        "timeout=%llu "
        "count=%zu",
        static_cast<unsigned long long>(startAddress),
        static_cast<unsigned long long>(endAddress),
        static_cast<unsigned long long>(timeoutMicros),
        maxInstructions
    );

    const uc_err err = uc_emu_start(
        uc,
        startAddress,
        endAddress,
        timeoutMicros,
        maxInstructions
    );

    const uc_err deleteErr =
        uc_hook_del(uc, invalidHook);

    if (deleteErr != UC_ERR_OK)
    {
        LOGW(
            "Failed to remove invalid-memory hook: %s",
            uc_strerror(deleteErr)
        );
    }

    if (err != UC_ERR_OK)
    {
        const uint32_t pc =
            readArmReg(uc, UC_ARM_REG_PC);

        LOGE(
            "uc_emu_start failed: "
            "error=%s "
            "PC=0x%08x",
            uc_strerror(err),
            pc
        );
    }

    return err;
}


// =============================================================================
// Execute exactly one instruction
// =============================================================================

uc_err vxp_step(
    uc_engine* uc)
{
    if (uc == nullptr)
    {
        LOGE("vxp_step: uc == nullptr");
        return UC_ERR_HANDLE;
    }

    uint32_t currentPc = 0;

    uc_err err = uc_reg_read(
        uc,
        UC_ARM_REG_PC,
        &currentPc
    );

    if (err != UC_ERR_OK)
    {
        LOGE(
            "vxp_step: failed to read PC: %s",
            uc_strerror(err)
        );

        return err;
    }

    LOGI(
        "vxp_step: PC=0x%08x",
        currentPc
    );

    //
    // count = 1 means exactly one instruction.
    //
    // We use the actual current PC as `begin`.
    //
    // The `until` value is irrelevant when count=1 unless execution
    // reaches it first.
    //
    constexpr uint64_t NO_END_ADDRESS_LIMIT =
        0xFFFFFFFFULL;

    err = uc_emu_start(
        uc,
        static_cast<uint64_t>(currentPc),
        NO_END_ADDRESS_LIMIT,
        0,
        1
    );

    if (err != UC_ERR_OK)
    {
        const uint32_t pcAfter =
            readArmReg(uc, UC_ARM_REG_PC);

        LOGE(
            "vxp_step failed: "
            "error=%s "
            "PC=0x%08x",
            uc_strerror(err),
            pcAfter
        );
    }

    return err;
}


// =============================================================================
// Stop emulation
// =============================================================================

void vxp_stop(
    uc_engine* uc)
{
    if (uc == nullptr)
        return;

    const uc_err err =
        uc_emu_stop(uc);

    if (err != UC_ERR_OK)
    {
        LOGE(
            "uc_emu_stop failed: %s",
            uc_strerror(err)
        );
    }
}