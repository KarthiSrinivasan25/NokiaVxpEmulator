#include <jni.h>

#include <android/log.h>

#include <cstdint>
#include <cstring>
#include <string>

#include <unicorn/unicorn.h>

#define LOG_TAG "VxpNative"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr uint32_t TRAP_BASE = 0xFEED0000u;
constexpr uint32_t TRAP_END  = 0xFEED1000u;

constexpr uint32_t TRAP_MALLOC                    = 0xFEED0000u;
constexpr uint32_t TRAP_FREE                      = 0xFEED0010u;
constexpr uint32_t TRAP_CALLOC                    = 0xFEED0020u;
constexpr uint32_t TRAP_REALLOC                   = 0xFEED0030u;
constexpr uint32_t TRAP_CREATE_TIMER              = 0xFEED0040u;

constexpr uint32_t TRAP_GET_TICK_COUNT            = 0xFEED0060u;
constexpr uint32_t TRAP_EXIT_APP                  = 0xFEED0080u;
constexpr uint32_t TRAP_REG_SYSEVT_CALLBACK       = 0xFEED0090u;
constexpr uint32_t TRAP_REG_KEYBOARD_CALLBACK     = 0xFEED00A0u;
constexpr uint32_t TRAP_REG_PEN_CALLBACK          = 0xFEED00B0u;
constexpr uint32_t TRAP_SWITCH_POWER_SAVING_MODE  = 0xFEED00C0u;

constexpr uint32_t TRAP_GET_SCREEN_WIDTH          = 0xFEED00D0u;
constexpr uint32_t TRAP_GET_SCREEN_HEIGHT         = 0xFEED00E0u;
constexpr uint32_t TRAP_GET_CHARACTER_HEIGHT      = 0xFEED00F0u;
constexpr uint32_t TRAP_GET_STRING_WIDTH          = 0xFEED0100u;

constexpr uint32_t TRAP_GRAPHIC_SETCOLOR          = 0xFEED0110u;
constexpr uint32_t TRAP_GRAPHIC_FILL_RECT_EX      = 0xFEED0120u;
constexpr uint32_t TRAP_GRAPHIC_TEXTOUT_TO_LAYER  = 0xFEED0130u;
constexpr uint32_t TRAP_GRAPHIC_CREATE_LAYER      = 0xFEED0140u;
constexpr uint32_t TRAP_GRAPHIC_CREATE_LAYER_CF   = 0xFEED0150u;
constexpr uint32_t TRAP_GRAPHIC_CREATE_CANVAS_CF  = 0xFEED0160u;
constexpr uint32_t TRAP_GRAPHIC_DELETE_LAYER      = 0xFEED0170u;
constexpr uint32_t TRAP_GRAPHIC_GET_CANVAS_BUFFER = 0xFEED0180u;
constexpr uint32_t TRAP_GRAPHIC_RELEASE_CANVAS    = 0xFEED0190u;
constexpr uint32_t TRAP_GRAPHIC_SET_CLIP          = 0xFEED01A0u;
constexpr uint32_t TRAP_GRAPHIC_SET_FONT          = 0xFEED01B0u;
constexpr uint32_t TRAP_GRAPHIC_FLUSH_LAYER       = 0xFEED01C0u;

constexpr uint32_t TRAP_KBD_SET_MODE              = 0xFEED01D0u;
constexpr uint32_t TRAP_INPUT_TEXT2               = 0xFEED01E0u;

constexpr uint32_t TRAP_FILE_OPEN                 = 0xFEED01F0u;
constexpr uint32_t TRAP_FILE_CLOSE                = 0xFEED0200u;
constexpr uint32_t TRAP_FILE_READ                 = 0xFEED0210u;
constexpr uint32_t TRAP_FILE_WRITE                = 0xFEED0220u;
constexpr uint32_t TRAP_FILE_COMMIT               = 0xFEED0230u;
constexpr uint32_t TRAP_FILE_GETFILESIZE          = 0xFEED0240u;
constexpr uint32_t TRAP_FILE_GET_ATTRIBUTES       = 0xFEED0250u;
constexpr uint32_t TRAP_FILE_MKDIR                = 0xFEED0260u;

constexpr uint32_t TRAP_FIND_FIRST                = 0xFEED0270u;
constexpr uint32_t TRAP_FIND_NEXT                 = 0xFEED0280u;
constexpr uint32_t TRAP_FIND_CLOSE                = 0xFEED0290u;

constexpr uint32_t TRAP_MIDI_PLAY_BY_BYTES        = 0xFEED02A0u;
constexpr uint32_t TRAP_MIDI_STOP                 = 0xFEED02B0u;

struct CpuContext {
    uc_engine *uc = nullptr;

    bool stopped = false;
    bool exitRequested = false;

    uint32_t screenWidth = 240;
    uint32_t screenHeight = 320;
    uint32_t characterHeight = 16;

    uint32_t nextHeap = 0x100000;
};

static bool isTrapAddress(uint64_t address) {
    return address >= TRAP_BASE && address < TRAP_END;
}

static const char *trapName(uint32_t address) {
    switch (address) {
        case TRAP_MALLOC:
            return "vm_malloc";

        case TRAP_FREE:
            return "vm_free";

        case TRAP_CALLOC:
            return "vm_calloc";

        case TRAP_REALLOC:
            return "vm_realloc";

        case TRAP_CREATE_TIMER:
            return "vm_create_timer";

        case TRAP_GET_TICK_COUNT:
            return "vm_get_tick_count";

        case TRAP_EXIT_APP:
            return "vm_exit_app";

        case TRAP_REG_SYSEVT_CALLBACK:
            return "vm_reg_sysevt_callback";

        case TRAP_REG_KEYBOARD_CALLBACK:
            return "vm_reg_keyboard_callback";

        case TRAP_REG_PEN_CALLBACK:
            return "vm_reg_pen_callback";

        case TRAP_SWITCH_POWER_SAVING_MODE:
            return "vm_switch_power_saving_mode";

        case TRAP_GET_SCREEN_WIDTH:
            return "vm_graphic_get_screen_width";

        case TRAP_GET_SCREEN_HEIGHT:
            return "vm_graphic_get_screen_height";

        case TRAP_GET_CHARACTER_HEIGHT:
            return "vm_graphic_get_character_height";

        case TRAP_GET_STRING_WIDTH:
            return "vm_graphic_get_string_width";

        case TRAP_GRAPHIC_SETCOLOR:
            return "vm_graphic_setcolor";

        case TRAP_GRAPHIC_FILL_RECT_EX:
            return "vm_graphic_fill_rect_ex";

        case TRAP_GRAPHIC_TEXTOUT_TO_LAYER:
            return "vm_graphic_textout_to_layer";

        case TRAP_GRAPHIC_CREATE_LAYER:
            return "vm_graphic_create_layer";

        case TRAP_GRAPHIC_CREATE_LAYER_CF:
            return "vm_graphic_create_layer_cf";

        case TRAP_GRAPHIC_CREATE_CANVAS_CF:
            return "vm_graphic_create_canvas_cf";

        case TRAP_GRAPHIC_DELETE_LAYER:
            return "vm_graphic_delete_layer";

        case TRAP_GRAPHIC_GET_CANVAS_BUFFER:
            return "vm_graphic_get_canvas_buffer";

        case TRAP_GRAPHIC_RELEASE_CANVAS:
            return "vm_graphic_release_canvas";

        case TRAP_GRAPHIC_SET_CLIP:
            return "vm_graphic_set_clip";

        case TRAP_GRAPHIC_SET_FONT:
            return "vm_graphic_set_font";

        case TRAP_GRAPHIC_FLUSH_LAYER:
            return "vm_graphic_flush_layer";

        case TRAP_KBD_SET_MODE:
            return "vm_kbd_set_mode";

        case TRAP_INPUT_TEXT2:
            return "vm_input_text2";

        case TRAP_FILE_OPEN:
            return "vm_file_open";

        case TRAP_FILE_CLOSE:
            return "vm_file_close";

        case TRAP_FILE_READ:
            return "vm_file_read";

        case TRAP_FILE_WRITE:
            return "vm_file_write";

        case TRAP_FILE_COMMIT:
            return "vm_file_commit";

        case TRAP_FILE_GETFILESIZE:
            return "vm_file_getfilesize";

        case TRAP_FILE_GET_ATTRIBUTES:
            return "vm_file_get_attributes";

        case TRAP_FILE_MKDIR:
            return "vm_file_mkdir";

        case TRAP_FIND_FIRST:
            return "vm_find_first";

        case TRAP_FIND_NEXT:
            return "vm_find_next";

        case TRAP_FIND_CLOSE:
            return "vm_find_close";

        case TRAP_MIDI_PLAY_BY_BYTES:
            return "vm_midi_play_by_bytes";

        case TRAP_MIDI_STOP:
            return "vm_midi_stop";

        default:
            return "unknown";
    }
}

static bool readReg(
        uc_engine *uc,
        int reg,
        uint32_t &value) {

    value = 0;

    uc_err err = uc_reg_read(
        uc,
        reg,
        &value
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_read(%d) failed: %s",
            reg,
            uc_strerror(err)
        );
        return false;
    }

    return true;
}

static bool writeReg(
        uc_engine *uc,
        int reg,
        uint32_t value) {

    uc_err err = uc_reg_write(
        uc,
        reg,
        &value
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_write(%d, 0x%08x) failed: %s",
            reg,
            value,
            uc_strerror(err)
        );
        return false;
    }

    return true;
}

static bool readR0(
        uc_engine *uc,
        uint32_t &v) {
    return readReg(uc, UC_ARM_REG_R0, v);
}

static bool readR1(
        uc_engine *uc,
        uint32_t &v) {
    return readReg(uc, UC_ARM_REG_R1, v);
}

static bool readR2(
        uc_engine *uc,
        uint32_t &v) {
    return readReg(uc, UC_ARM_REG_R2, v);
}

static bool readR3(
        uc_engine *uc,
        uint32_t &v) {
    return readReg(uc, UC_ARM_REG_R3, v);
}

static bool writeR0(
        uc_engine *uc,
        uint32_t v) {
    return writeReg(uc, UC_ARM_REG_R0, v);
}

static bool returnToGuest(
        uc_engine *uc) {

    uint32_t lr = 0;

    if (!readReg(
            uc,
            UC_ARM_REG_LR,
            lr)) {
        return false;
    }

    /*
     * LR contains the return address.
     *
     * Keep bit 0 because it contains the ARM/Thumb
     * state information used by the guest ABI.
     */
    if (!writeReg(
            uc,
            UC_ARM_REG_PC,
            lr)) {
        return false;
    }

    return true;
}

static bool returnWithValue(
        uc_engine *uc,
        uint32_t result) {

    if (!writeR0(uc, result)) {
        return false;
    }

    return returnToGuest(uc);
}

static uint32_t fakeAlloc(
        CpuContext *ctx,
        uint32_t size) {

    if (size == 0) {
        return 0;
    }

    /*
     * The real implementation should use the VM heap manager.
     *
     * This fallback only prevents a NULL return from causing
     * immediate guest failure while the native VM allocator
     * is being integrated.
     */
    uint32_t aligned =
        (size + 7u) & ~7u;

    uint32_t result =
        ctx->nextHeap;

    ctx->nextHeap += aligned;

    return result;
}

static bool dispatchTrap(
        uc_engine *uc,
        uint32_t address,
        CpuContext *ctx) {

    LOGI(
        "Guest VM call: %s @ 0x%08x",
        trapName(address),
        address
    );

    switch (address) {

        /*
         * ------------------------------------------------------
         * MEMORY
         * ------------------------------------------------------
         */

        case TRAP_MALLOC: {
            uint32_t size = 0;

            if (!readR0(uc, size)) {
                return false;
            }

            uint32_t ptr =
                fakeAlloc(ctx, size);

            LOGI(
                "vm_malloc(%u) -> 0x%08x",
                size,
                ptr
            );

            return returnWithValue(
                uc,
                ptr
            );
        }

        case TRAP_CALLOC: {
            uint32_t count = 0;
            uint32_t size = 0;

            if (!readR0(uc, count) ||
                !readR1(uc, size)) {
                return false;
            }

            uint64_t total =
                static_cast<uint64_t>(count) *
                static_cast<uint64_t>(size);

            if (total > 0xFFFFFFFFull) {
                return returnWithValue(
                    uc,
                    0
                );
            }

            uint32_t ptr =
                fakeAlloc(
                    ctx,
                    static_cast<uint32_t>(total)
                );

            LOGI(
                "vm_calloc(%u,%u) -> 0x%08x",
                count,
                size,
                ptr
            );

            return returnWithValue(
                uc,
                ptr
            );
        }

        case TRAP_FREE: {
            uint32_t ptr = 0;

            if (!readR0(uc, ptr)) {
                return false;
            }

            LOGI(
                "vm_free(0x%08x)",
                ptr
            );

            /*
             * No-op until the real guest heap allocator is connected.
             */
            return returnWithValue(
                uc,
                0
            );
        }

        case TRAP_REALLOC: {
            uint32_t ptr = 0;
            uint32_t size = 0;

            if (!readR0(uc, ptr) ||
                !readR1(uc, size)) {
                return false;
            }

            if (ptr == 0) {
                uint32_t result =
                    fakeAlloc(ctx, size);

                return returnWithValue(
                    uc,
                    result
                );
            }

            /*
             * Temporary compatibility implementation.
             */
            LOGW(
                "vm_realloc(0x%08x,%u) using compatibility allocator",
                ptr,
                size
            );

            uint32_t result =
                fakeAlloc(ctx, size);

            return returnWithValue(
                uc,
                result
            );
        }

        /*
         * ------------------------------------------------------
         * SYSTEM
         * ------------------------------------------------------
         */

        case TRAP_CREATE_TIMER: {
            LOGI(
                "vm_create_timer()"
            );

            return returnWithValue(
                uc,
                0
            );
        }

        case TRAP_GET_TICK_COUNT: {
            /*
             * Android monotonic time in milliseconds.
             */
            struct timespec ts{};

            clock_gettime(
                CLOCK_MONOTONIC,
                &ts
            );

            uint64_t ms =
                static_cast<uint64_t>(
                    ts.tv_sec
                ) * 1000ull +
                static_cast<uint64_t>(
                    ts.tv_nsec
                ) / 1000000ull;

            return returnWithValue(
                uc,
                static_cast<uint32_t>(ms)
            );
        }

        case TRAP_EXIT_APP: {
            LOGI(
                "vm_exit_app()"
            );

            ctx->exitRequested = true;
            ctx->stopped = true;

            return returnWithValue(
                uc,
                0
            );
        }

        case TRAP_REG_SYSEVT_CALLBACK:
        case TRAP_REG_KEYBOARD_CALLBACK:
        case TRAP_REG_PEN_CALLBACK: {
            uint32_t callback = 0;

            if (!readR0(
                    uc,
                    callback)) {
                return false;
            }

            LOGI(
                "%s(0x%08x)",
                trapName(address),
                callback
            );

            return returnWithValue(
                uc,
                0
            );
        }

        case TRAP_SWITCH_POWER_SAVING_MODE: {
            LOGI(
                "vm_switch_power_saving_mode()"
            );

            return returnWithValue(
                uc,
                0
            );
        }

        /*
         * ------------------------------------------------------
         * GRAPHICS
         * ------------------------------------------------------
         */

        case TRAP_GET_SCREEN_WIDTH:
            return returnWithValue(
                uc,
                ctx->screenWidth
            );

        case TRAP_GET_SCREEN_HEIGHT:
            return returnWithValue(
                uc,
                ctx->screenHeight
            );

        case TRAP_GET_CHARACTER_HEIGHT:
            return returnWithValue(
                uc,
                ctx->characterHeight
            );

        case TRAP_GET_STRING_WIDTH: {
            uint32_t text = 0;

            if (!readR0(
                    uc,
                    text)) {
                return false;
            }

            /*
             * Conservative fallback.
             */
            return returnWithValue(
                uc,
                0
            );
        }

        case TRAP_GRAPHIC_SETCOLOR:
        case TRAP_GRAPHIC_FILL_RECT_EX:
        case TRAP_GRAPHIC_TEXTOUT_TO_LAYER:
        case TRAP_GRAPHIC_CREATE_LAYER:
        case TRAP_GRAPHIC_CREATE_LAYER_CF:
        case TRAP_GRAPHIC_CREATE_CANVAS_CF:
        case TRAP_GRAPHIC_DELETE_LAYER:
        case TRAP_GRAPHIC_GET_CANVAS_BUFFER:
        case TRAP_GRAPHIC_RELEASE_CANVAS:
        case TRAP_GRAPHIC_SET_CLIP:
        case TRAP_GRAPHIC_SET_FONT:
        case TRAP_GRAPHIC_FLUSH_LAYER: {

            /*
             * These calls are recognized but require the Android
             * rendering backend to be connected.
             */
            LOGW(
                "%s() compatibility implementation",
                trapName(address)
            );

            return returnWithValue(
                uc,
                0
            );
        }

        /*
         * ------------------------------------------------------
         * KEYBOARD / INPUT
         * ------------------------------------------------------
         */

        case TRAP_KBD_SET_MODE:
        case TRAP_INPUT_TEXT2: {

            LOGI(
                "%s()",
                trapName(address)
            );

            return returnWithValue(
                uc,
                0
            );
        }

        /*
         * ------------------------------------------------------
         * FILE SYSTEM
         * ------------------------------------------------------
         */

        case TRAP_FILE_OPEN:
        case TRAP_FILE_CLOSE:
        case TRAP_FILE_READ:
        case TRAP_FILE_WRITE:
        case TRAP_FILE_COMMIT:
        case TRAP_FILE_GETFILESIZE:
        case TRAP_FILE_GET_ATTRIBUTES:
        case TRAP_FILE_MKDIR:
        case TRAP_FIND_FIRST:
        case TRAP_FIND_NEXT:
        case TRAP_FIND_CLOSE: {

            /*
             * IMPORTANT:
             *
             * Previously vm_file_mkdir() returned to the emulator
             * while PC was still 0xFEED0260, causing:
             *
             * FETCH_UNMAPPED @ 0xFEED0260
             *
             * Every recognized trap now explicitly returns through
             * LR.
             */

            LOGW(
                "%s() - file system compatibility implementation",
                trapName(address)
            );

            /*
             * For operations that normally return a handle,
             * zero means failure/no handle.
             *
             * For mkdir/write/etc. this is also a safe temporary
             * error result until the real VXP filesystem is added.
             */
            uint32_t result = 0;

            if (address == TRAP_FILE_MKDIR) {
                LOGW(
                    "vm_file_mkdir() - file system not implemented yet"
                );

                /*
                 * If your VXP ABI defines a specific failure value,
                 * replace this with that value.
                 */
                result = 0xFFFFFFFFu;
            }

            return returnWithValue(
                uc,
                result
            );
        }

        /*
         * ------------------------------------------------------
         * MIDI
         * ------------------------------------------------------
         */

        case TRAP_MIDI_PLAY_BY_BYTES:
        case TRAP_MIDI_STOP: {

            LOGI(
                "%s()",
                trapName(address)
            );

            return returnWithValue(
                uc,
                0
            );
        }

        default:
            break;
    }

    LOGE(
        "Unhandled guest trap: 0x%08x",
        address
    );

    return false;
}


/*
 * ------------------------------------------------------------
 * NORMAL CODE HOOK
 * ------------------------------------------------------------
 *
 * This signature is compatible with Unicorn 2.1.
 */
static void codeHook(
        uc_engine *uc,
        uint64_t address,
        uint32_t size,
        void *userData) {

    (void)uc;
    (void)size;
    (void)userData;

    /*
     * Do not attempt to dispatch FEED addresses here.
     *
     * An unmapped FEED fetch is handled by unmappedFetchHook().
     */
}


/*
 * ------------------------------------------------------------
 * UNMAPPED FETCH HOOK
 * ------------------------------------------------------------
 *
 * This is the critical fix for:
 *
 * FETCH_UNMAPPED @ 0xFEED0260
 *
 * Unicorn 2.1 callback signature:
 *
 * bool (*)(uc_engine *,
 *          uc_mem_type,
 *          uint64_t,
 *          int,
 *          int64_t,
 *          void *)
 */
static bool unmappedFetchHook(
        uc_engine *uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void *userData) {

    (void)value;

    /*
     * We only want instruction fetches.
     */
    if (type != UC_MEM_FETCH_UNMAPPED) {
        return false;
    }

    /*
     * Normal unmapped memory should remain a real Unicorn fault.
     */
    if (!isTrapAddress(address)) {
        return false;
    }

    auto *ctx =
        static_cast<CpuContext *>(userData);

    if (ctx == nullptr) {
        LOGE(
            "FEED trap received with null CPU context"
        );
        return false;
    }

    const uint32_t trap =
        static_cast<uint32_t>(address);

    LOGI(
        "Guest-call trap: 0x%08x (%s), fetch-size=%d",
        trap,
        trapName(trap),
        size
    );

    bool handled =
        dispatchTrap(
            uc,
            trap,
            ctx
        );

    if (!handled) {
        LOGE(
            "Guest-call dispatch FAILED: 0x%08x (%s)",
            trap,
            trapName(trap)
        );

        return false;
    }

    /*
     * IMPORTANT:
     *
     * The handler changed PC to LR.
     *
     * Unicorn will now continue from the guest return address
     * rather than trying to execute 0xFEEDxxxx.
     */
    return true;
}


/*
 * ------------------------------------------------------------
 * MEMORY FAULT DIAGNOSTICS
 * ------------------------------------------------------------
 */

static bool memoryFaultHook(
        uc_engine *uc,
        uc_mem_type type,
        uint64_t address,
        int size,
        int64_t value,
        void *userData) {

    (void)userData;

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

    const char *kind =
        "UNKNOWN";

    switch (type) {
        case UC_MEM_READ_UNMAPPED:
            kind = "READ_UNMAPPED";
            break;

        case UC_MEM_WRITE_UNMAPPED:
            kind = "WRITE_UNMAPPED";
            break;

        case UC_MEM_FETCH_UNMAPPED:
            kind = "FETCH_UNMAPPED";
            break;

        case UC_MEM_READ_PROT:
            kind = "READ_PROT";
            break;

        case UC_MEM_WRITE_PROT:
            kind = "WRITE_PROT";
            break;

        case UC_MEM_FETCH_PROT:
            kind = "FETCH_PROT";
            break;

        default:
            break;
    }

    LOGE(
        "MEMORY FAULT: %s at guest address=0x%llx "
        "size=%d value=0x%llx | PC=0x%08x SP=0x%08x LR=0x%08x",
        kind,
        static_cast<unsigned long long>(address),
        size,
        static_cast<unsigned long long>(value),
        pc,
        sp,
        lr
    );

    /*
     * Let Unicorn report the fault normally.
     */
    return false;
}

} // namespace


/*
 * ============================================================
 * JNI
 * ============================================================
 *
 * These functions give you a standalone CPU bridge.
 *
 * If your Java/Kotlin layer already calls differently named JNI
 * functions, keep those existing entry points and use the native
 * initialization/run functions above.
 */

extern "C"
JNIEXPORT jlong JNICALL
Java_com_nokia_vxp_VxpNative_nativeCreate(
        JNIEnv *,
        jclass) {

    auto *ctx =
        new CpuContext();

    uc_err err =
        uc_open(
            UC_ARCH_ARM,
            UC_MODE_ARM,
            &ctx->uc
        );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_open() failed: %s",
            uc_strerror(err)
        );

        delete ctx;
        return 0;
    }

    LOGI(
        "Unicorn ARM engine created (lib version 2.1)"
    );

    /*
     * Normal code hook.
     */
    uc_hook codeHookHandle{};

    err = uc_hook_add(
        ctx->uc,
        &codeHookHandle,
        UC_HOOK_CODE,

        /*
         * Unicorn's public API uses void* for callback.
         *
         * This explicit cast fixes the compilation error from
         * Unicorn 2.1 / clang:
         *
         * no matching function for call to uc_hook_add
         */
        reinterpret_cast<void *>(codeHook),

        ctx,
        1,
        0
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_hook_add(CODE) failed: %s",
            uc_strerror(err)
        );

        uc_close(ctx->uc);
        delete ctx;
        return 0;
    }

    /*
     * Guest VM call trap hook.
     */
    uc_hook fetchHookHandle{};

    err = uc_hook_add(
        ctx->uc,
        &fetchHookHandle,
        UC_HOOK_MEM_FETCH_UNMAPPED,
        reinterpret_cast<void *>(unmappedFetchHook),
        ctx,
        1,
        0
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_hook_add(FETCH_UNMAPPED) failed: %s",
            uc_strerror(err)
        );

        uc_close(ctx->uc);
        delete ctx;
        return 0;
    }

    /*
     * Memory diagnostics.
     */
    uc_hook faultHookHandle{};

    err = uc_hook_add(
        ctx->uc,
        &faultHookHandle,
        UC_HOOK_MEM_UNMAPPED |
        UC_HOOK_MEM_PROT,
        reinterpret_cast<void *>(memoryFaultHook),
        ctx,
        1,
        0
    );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_hook_add(MEMORY) failed: %s",
            uc_strerror(err)
        );

        uc_close(ctx->uc);
        delete ctx;
        return 0;
    }

    LOGI(
        "Guest-call dispatch trap installed"
    );

    LOGI(
        "Fault diagnostics hook installed"
    );

    return reinterpret_cast<jlong>(ctx);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_nokia_vxp_VxpNative_nativeDestroy(
        JNIEnv *,
        jclass,
        jlong handle) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr) {
        return;
    }

    if (ctx->uc != nullptr) {
        uc_close(ctx->uc);
        ctx->uc = nullptr;
    }

    delete ctx;

    LOGI(
        "Unicorn ARM engine destroyed"
    );
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_VxpNative_nativeMap(
        JNIEnv *,
        jclass,
        jlong handle,
        jlong address,
        jlong size,
        jint perms) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr ||
        ctx->uc == nullptr) {
        return static_cast<jint>(
            UC_ERR_HANDLE
        );
    }

    if (address < 0 ||
        size <= 0 ||
        address > 0xFFFFFFFFll) {
        return static_cast<jint>(
            UC_ERR_ARG
        );
    }

    uint64_t base =
        static_cast<uint64_t>(address);

    uint64_t length =
        static_cast<uint64_t>(size);

    /*
     * Unicorn requires page aligned mapping.
     */
    constexpr uint64_t PAGE = 0x1000ull;

    uint64_t alignedBase =
        base & ~(PAGE - 1ull);

    uint64_t end =
        (base + length + PAGE - 1ull)
        & ~(PAGE - 1ull);

    uint64_t alignedSize =
        end - alignedBase;

    if (alignedSize == 0) {
        return static_cast<jint>(
            UC_ERR_ARG
        );
    }

    uc_err err =
        uc_mem_map(
            ctx->uc,
            alignedBase,
            alignedSize,
            static_cast<uint32_t>(perms)
        );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_mem_map(base=0x%llx,size=0x%llx) failed: %s",
            static_cast<unsigned long long>(alignedBase),
            static_cast<unsigned long long>(alignedSize),
            uc_strerror(err)
        );

        return static_cast<jint>(err);
    }

    LOGI(
        "Mapped region: logicalBase=0x%llx size=0x%llx "
        "(aligned base=0x%llx size=0x%llx) perms=%d",
        static_cast<unsigned long long>(base),
        static_cast<unsigned long long>(length),
        static_cast<unsigned long long>(alignedBase),
        static_cast<unsigned long long>(alignedSize),
        perms
    );

    return static_cast<jint>(
        UC_ERR_OK
    );
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_VxpNative_nativeWriteMemory(
        JNIEnv *env,
        jclass,
        jlong handle,
        jlong address,
        jbyteArray data) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr ||
        ctx->uc == nullptr ||
        data == nullptr) {
        return static_cast<jint>(
            UC_ERR_ARG
        );
    }

    jsize length =
        env->GetArrayLength(data);

    if (length <= 0) {
        return static_cast<jint>(
            UC_ERR_OK
        );
    }

    jbyte *bytes =
        env->GetByteArrayElements(
            data,
            nullptr
        );

    if (bytes == nullptr) {
        return static_cast<jint>(
            UC_ERR_NOMEM
        );
    }

    uc_err err =
        uc_mem_write(
            ctx->uc,
            static_cast<uint64_t>(address),
            bytes,
            static_cast<size_t>(length)
        );

    env->ReleaseByteArrayElements(
        data,
        bytes,
        JNI_ABORT
    );

    return static_cast<jint>(err);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_VxpNative_nativeSetRegister(
        JNIEnv *,
        jclass,
        jlong handle,
        jint reg,
        jint value) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr ||
        ctx->uc == nullptr) {
        return static_cast<jint>(
            UC_ERR_HANDLE
        );
    }

    uint32_t v =
        static_cast<uint32_t>(value);

    return static_cast<jint>(
        uc_reg_write(
            ctx->uc,
            reg,
            &v
        )
    );
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_VxpNative_nativeGetRegister(
        JNIEnv *,
        jclass,
        jlong handle,
        jint reg) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr ||
        ctx->uc == nullptr) {
        return 0;
    }

    uint32_t v = 0;

    uc_err err =
        uc_reg_read(
            ctx->uc,
            reg,
            &v
        );

    if (err != UC_ERR_OK) {
        LOGE(
            "uc_reg_read failed: %s",
            uc_strerror(err)
        );
        return 0;
    }

    return static_cast<jint>(v);
}


extern "C"
JNIEXPORT jint JNICALL
Java_com_nokia_vxp_VxpNative_nativeRun(
        JNIEnv *,
        jclass,
        jlong handle,
        jlong start,
        jlong end) {

    auto *ctx =
        reinterpret_cast<CpuContext *>(handle);

    if (ctx == nullptr ||
        ctx->uc == nullptr) {
        return static_cast<jint>(
            UC_ERR_HANDLE
        );
    }

    ctx->stopped = false;
    ctx->exitRequested = false;

    LOGI(
        "Starting Unicorn: start=0x%llx end=0x%llx",
        static_cast<unsigned long long>(start),
        static_cast<unsigned long long>(end)
    );

    uc_err err =
        uc_emu_start(
            ctx->uc,
            static_cast<uint64_t>(start),
            static_cast<uint64_t>(end),
            0,
            0
        );

    if (err != UC_ERR_OK) {

        uint32_t pc = 0;
        uint32_t sp = 0;
        uint32_t lr = 0;

        uc_reg_read(
            ctx->uc,
            UC_ARM_REG_PC,
            &pc
        );

        uc_reg_read(
            ctx->uc,
            UC_ARM_REG_SP,
            &sp
        );

        uc_reg_read(
            ctx->uc,
            UC_ARM_REG_LR,
            &lr
        );

        LOGE(
            "uc_emu_start(start=0x%llx,end=0x%llx) failed: %s "
            "PC=0x%08x SP=0x%08x LR=0x%08x",
            static_cast<unsigned long long>(start),
            static_cast<unsigned long long>(end),
            uc_strerror(err),
            pc,
            sp,
            lr
        );
    }

    if (ctx->exitRequested) {
        LOGI(
            "Guest requested application exit"
        );
    }

    return static_cast<jint>(err);
}