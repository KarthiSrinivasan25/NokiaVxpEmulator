#include "arm_cpu.h"

// ---------------------------------------------------------------------------
// Small local helpers
// ---------------------------------------------------------------------------

namespace {

inline uint32_t rorImm(uint32_t value, uint32_t amount) {
    amount &= 31u;
    if (amount == 0) return value;
    return (value >> amount) | (value << (32u - amount));
}

inline bool bit(uint32_t word, int n) { return ((word >> n) & 1u) != 0; }
inline uint32_t bits(uint32_t word, int hi, int lo) { return (word >> lo) & ((1u << (hi - lo + 1)) - 1u); }

inline int32_t signExtend(uint32_t value, int fromBits) {
    uint32_t shift = 32 - fromBits;
    return static_cast<int32_t>(value << shift) >> shift;
}

} // namespace

// ---------------------------------------------------------------------------
// Bus wrappers - see the doc comment on these in arm_cpu.h. Every exec*()
// function below calls these instead of bus_ directly so lastFaultAddress_
// always reflects the real data address a read/write fault happened at,
// not just the instruction's own PC.
// ---------------------------------------------------------------------------

bool ArmCpu::doRead(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) {
    if (bus_->read(address, out, len, fault)) return true;
    lastFaultAddress_ = address;
    return false;
}

bool ArmCpu::doWrite(uint32_t address, const uint8_t* data, size_t len, ArmFault* fault) {
    if (bus_->write(address, data, len, fault)) return true;
    lastFaultAddress_ = address;
    return false;
}

bool ArmCpu::doFetch(uint32_t address, uint8_t* out, size_t len, ArmFault* fault) {
    if (bus_->fetch(address, out, len, fault)) return true;
    lastFaultAddress_ = address;
    return false;
}

// ---------------------------------------------------------------------------
// Register access. PC reads as address-of-current-instruction + 8 (ARM) or
// +4 (Thumb) per the classic ARM7TDMI pipeline convention that real guest
// code (and plenty of ROM/relocation math) is written to expect.
// ---------------------------------------------------------------------------

uint32_t ArmCpu::getReg(int regId) const {
    if (regId == ARM_CPSR) return cpsr_;
    if (regId < 0 || regId > 15) return 0;
    if (regId == ARM_PC) {
        // r_[15] is kept as "address of the instruction currently being
        // executed" throughout stepArm()/stepThumb() (it's only advanced
        // to the next instruction at the very end of each exec* function).
        // Reading R15 as an operand needs the classic ARM7TDMI pipeline
        // bias on top of that: current+8 in ARM state, current+4 in Thumb.
        return r_[ARM_PC] + (thumbMode() ? 4u : 8u);
    }
    return r_[regId];
}

void ArmCpu::setReg(int regId, uint32_t value) {
    if (regId == ARM_CPSR) { cpsr_ = value; return; }
    if (regId < 0 || regId > 15) return;
    r_[regId] = value;
}

// ---------------------------------------------------------------------------
// Flags
// ---------------------------------------------------------------------------

void ArmCpu::setNZ(uint32_t result) {
    cpsr_ = (result & 0x80000000u) ? (cpsr_ | (1u << CPSR_BIT_N)) : (cpsr_ & ~(1u << CPSR_BIT_N));
    cpsr_ = (result == 0) ? (cpsr_ | (1u << CPSR_BIT_Z)) : (cpsr_ & ~(1u << CPSR_BIT_Z));
}

void ArmCpu::setNZCV_add(uint32_t a, uint32_t b, uint32_t result) {
    setNZ(result);
    bool carry = result < a; // unsigned overflow
    bool overflow = (~(a ^ b) & (a ^ result)) & 0x80000000u;
    cpsr_ = carry ? (cpsr_ | (1u << CPSR_BIT_C)) : (cpsr_ & ~(1u << CPSR_BIT_C));
    cpsr_ = overflow ? (cpsr_ | (1u << CPSR_BIT_V)) : (cpsr_ & ~(1u << CPSR_BIT_V));
}

void ArmCpu::setNZCV_sub(uint32_t a, uint32_t b, uint32_t result) {
    setNZ(result);
    bool carry = a >= b; // NOT borrow, ARM's C flag on SUB means "no borrow"
    bool overflow = ((a ^ b) & (a ^ result)) & 0x80000000u;
    cpsr_ = carry ? (cpsr_ | (1u << CPSR_BIT_C)) : (cpsr_ & ~(1u << CPSR_BIT_C));
    cpsr_ = overflow ? (cpsr_ | (1u << CPSR_BIT_V)) : (cpsr_ & ~(1u << CPSR_BIT_V));
}

bool ArmCpu::conditionPassed(uint32_t cond) const {
    bool n = bit(cpsr_, CPSR_BIT_N), z = bit(cpsr_, CPSR_BIT_Z);
    bool c = bit(cpsr_, CPSR_BIT_C), v = bit(cpsr_, CPSR_BIT_V);
    switch (cond) {
        case 0x0: return z;               // EQ
        case 0x1: return !z;              // NE
        case 0x2: return c;               // CS/HS
        case 0x3: return !c;              // CC/LO
        case 0x4: return n;               // MI
        case 0x5: return !n;              // PL
        case 0x6: return v;               // VS
        case 0x7: return !v;              // VC
        case 0x8: return c && !z;         // HI
        case 0x9: return !c || z;         // LS
        case 0xA: return n == v;          // GE
        case 0xB: return n != v;          // LT
        case 0xC: return !z && (n == v);  // GT
        case 0xD: return z || (n != v);   // LE
        case 0xE: return true;            // AL
        default:  return false;           // NV - reserved, never executes
    }
}

// ---------------------------------------------------------------------------
// Top-level step
// ---------------------------------------------------------------------------

ArmFault ArmCpu::stepOne() {
    return thumbMode() ? stepThumb() : stepArm();
}

// ---------------------------------------------------------------------------
// ARM (32-bit) decode/execute
// ---------------------------------------------------------------------------

ArmFault ArmCpu::stepArm() {
    uint32_t pc = r_[ARM_PC];
    if (pc & 0x3) {
        // Misaligned ARM PC - not something well-formed guest code should
        // produce; treat as an invalid-instruction fault rather than
        // silently masking the low bits, so it's visible during bring-up.
        return ArmFault::InvalidInsn;
    }

    uint8_t raw[4];
    ArmFault fault = ArmFault::None;
    if (!doFetch(pc, raw, 4, &fault)) return fault;
    uint32_t insn = static_cast<uint32_t>(raw[0]) | (static_cast<uint32_t>(raw[1]) << 8) |
                     (static_cast<uint32_t>(raw[2]) << 16) | (static_cast<uint32_t>(raw[3]) << 24);

    uint32_t cond = bits(insn, 31, 28);
    if (cond != 0xF && !conditionPassed(cond)) {
        // Condition failed: instruction is a no-op except for advancing PC.
        r_[ARM_PC] = pc + 4;
        return ArmFault::None;
    }
    if (cond == 0xF) {
        // Unconditional-encoding space (BLX etc.) - not needed by any
        // real MRE-era binary target; treat as invalid rather than guess.
        return ArmFault::InvalidInsn;
    }

    // Branch and Exchange: cond 0001 0010 1111 1111 1111 0001 Rm
    if ((insn & 0x0FFFFFF0u) == 0x012FFF10u) {
        return execBranchExchange(insn);
    }

    // Multiply / Multiply-Accumulate: bits27:22=000000, bits7:4=1001
    if (bits(insn, 27, 22) == 0 && bits(insn, 7, 4) == 0b1001) {
        return execMultiply(insn);
    }

    // Halfword/signed data transfer: bits27:25=000, bit7=1, bit4=1
    if (bits(insn, 27, 25) == 0b000 && bit(insn, 7) && bit(insn, 4)) {
        return execHalfwordSignedTransfer(insn);
    }

    // Branch / Branch-with-Link: bits27:25 = 101
    if (bits(insn, 27, 25) == 0b101) {
        return execBranch(insn);
    }

    // Block data transfer (LDM/STM): bits27:25 = 100
    if (bits(insn, 27, 25) == 0b100) {
        return execBlockDataTransfer(insn);
    }

    // Single data transfer (LDR/STR): bits27:26 = 01
    if (bits(insn, 27, 26) == 0b01) {
        return execSingleDataTransfer(insn);
    }

    // Data processing: bits27:26 = 00 (and not one of the special forms above)
    if (bits(insn, 27, 26) == 0b00) {
        return execDataProcessing(insn);
    }

    return ArmFault::InvalidInsn;
}

ArmCpu::Shifted ArmCpu::shifterOperand(uint32_t insn, bool immediateForm) {
    bool carryIn = bit(cpsr_, CPSR_BIT_C);

    if (immediateForm) {
        uint32_t rotate = bits(insn, 11, 8) * 2;
        uint32_t imm8 = bits(insn, 7, 0);
        uint32_t value = rorImm(imm8, rotate);
        bool carryOut = (rotate == 0) ? carryIn : ((value & 0x80000000u) != 0);
        return {value, carryOut};
    }

    uint32_t rm = bits(insn, 3, 0);
    uint32_t rmVal = getReg(rm); // getReg() applies the PC+8 convention automatically
    uint32_t shiftType = bits(insn, 6, 5);
    bool shiftByReg = bit(insn, 4);
    uint32_t shiftAmount;

    if (shiftByReg) {
        uint32_t rs = bits(insn, 11, 8);
        shiftAmount = getReg(rs) & 0xFFu;
        // Rm read for a register-specified shift also follows the PC+8/+12
        // "old ARM7TDMI quirk" only for genuinely pipelined reads; using
        // PC+8 uniformly (via getReg) is the documented, commonly-relied-on
        // behavior and matches what real toolchains assume.
    } else {
        shiftAmount = bits(insn, 11, 7);
    }

    switch (shiftType) {
        case 0b00: { // LSL
            if (shiftAmount == 0) return {rmVal, carryIn};
            if (shiftAmount >= 32) return {0, shiftAmount == 32 ? bit(rmVal, 0) : false};
            bool carryOut = bit(rmVal, 32 - static_cast<int>(shiftAmount));
            return {rmVal << shiftAmount, carryOut};
        }
        case 0b01: { // LSR
            if (shiftAmount == 0) shiftAmount = shiftByReg ? 0 : 32;
            if (shiftAmount == 0) return {rmVal, carryIn};
            if (shiftAmount >= 32) return {0, shiftAmount == 32 ? bit(rmVal, 31) : false};
            bool carryOut = bit(rmVal, static_cast<int>(shiftAmount) - 1);
            return {rmVal >> shiftAmount, carryOut};
        }
        case 0b10: { // ASR
            if (shiftAmount == 0) shiftAmount = shiftByReg ? 0 : 32;
            if (shiftAmount == 0) return {rmVal, carryIn};
            if (shiftAmount >= 32) {
                bool signBit = bit(rmVal, 31);
                return {signBit ? 0xFFFFFFFFu : 0u, signBit};
            }
            bool carryOut = bit(rmVal, static_cast<int>(shiftAmount) - 1);
            int32_t signedVal = static_cast<int32_t>(rmVal);
            return {static_cast<uint32_t>(signedVal >> shiftAmount), carryOut};
        }
        default: { // 0b11: ROR (or RRX when shiftAmount==0 and immediate form)
            if (shiftAmount == 0) {
                if (shiftByReg) return {rmVal, carryIn}; // RS==0 -> no-op shift
                // RRX: rotate right through carry by 1
                uint32_t result = (rmVal >> 1) | (carryIn ? 0x80000000u : 0u);
                bool carryOut = bit(rmVal, 0);
                return {result, carryOut};
            }
            shiftAmount &= 31u;
            if (shiftAmount == 0) return {rmVal, bit(rmVal, 31)};
            uint32_t result = rorImm(rmVal, shiftAmount);
            bool carryOut = bit(rmVal, static_cast<int>(shiftAmount) - 1);
            return {result, carryOut};
        }
    }
}

ArmFault ArmCpu::execDataProcessing(uint32_t insn) {
    bool immediate = bit(insn, 25);
    uint32_t opcode = bits(insn, 24, 21);
    bool setFlags = bit(insn, 20);
    uint32_t rn = bits(insn, 19, 16);
    uint32_t rd = bits(insn, 15, 12);

    Shifted op2 = shifterOperand(insn, immediate);
    uint32_t rnVal = getReg(rn);
    uint32_t result = 0;
    bool writesRd = true;

    switch (opcode) {
        case 0x0: result = rnVal & op2.value; break;                    // AND
        case 0x1: result = rnVal ^ op2.value; break;                    // EOR
        case 0x2: result = rnVal - op2.value; break;                    // SUB
        case 0x3: result = op2.value - rnVal; break;                    // RSB
        case 0x4: result = rnVal + op2.value; break;                    // ADD
        case 0x5: { // ADC
            uint64_t sum = static_cast<uint64_t>(rnVal) + op2.value + (bit(cpsr_, CPSR_BIT_C) ? 1 : 0);
            result = static_cast<uint32_t>(sum);
            break;
        }
        case 0x6: { // SBC: Rn - op2 - NOT(C)
            uint64_t diff = static_cast<uint64_t>(rnVal) - op2.value - (bit(cpsr_, CPSR_BIT_C) ? 0 : 1);
            result = static_cast<uint32_t>(diff);
            break;
        }
        case 0x7: { // RSC: op2 - Rn - NOT(C)
            uint64_t diff = static_cast<uint64_t>(op2.value) - rnVal - (bit(cpsr_, CPSR_BIT_C) ? 0 : 1);
            result = static_cast<uint32_t>(diff);
            break;
        }
        case 0x8: result = rnVal & op2.value; writesRd = false; break;  // TST
        case 0x9: result = rnVal ^ op2.value; writesRd = false; break;  // TEQ
        case 0xA: result = rnVal - op2.value; writesRd = false; break;  // CMP
        case 0xB: result = rnVal + op2.value; writesRd = false; break;  // CMN
        case 0xC: result = rnVal | op2.value; break;                    // ORR
        case 0xD: result = op2.value; break;                            // MOV
        case 0xE: result = rnVal & ~op2.value; break;                   // BIC
        case 0xF: result = ~op2.value; break;                           // MVN
        default: return ArmFault::InvalidInsn;
    }

    if (setFlags) {
        switch (opcode) {
            case 0x2: case 0xA: setNZCV_sub(rnVal, op2.value, result); break;              // SUB/CMP
            case 0x3: setNZCV_sub(op2.value, rnVal, result); break;                        // RSB
            case 0x4: case 0xB: setNZCV_add(rnVal, op2.value, result); break;              // ADD/CMN
            case 0x5: setNZCV_add(rnVal, op2.value, result); break;                        // ADC (carry-in ignored in V/C approx for simplicity)
            case 0x6: setNZCV_sub(rnVal, op2.value, result); break;                        // SBC
            case 0x7: setNZCV_sub(op2.value, rnVal, result); break;                        // RSC
            default:
                // Logical ops (AND/EOR/TST/TEQ/ORR/MOV/BIC/MVN): N,Z from
                // result, C from the shifter's carry-out, V unaffected.
                setNZ(result);
                cpsr_ = op2.carryOut ? (cpsr_ | (1u << CPSR_BIT_C)) : (cpsr_ & ~(1u << CPSR_BIT_C));
                break;
        }
    }

    if (writesRd) {
        setReg(rd, result);
        if (rd == ARM_PC) {
            // Writing PC directly (common in MOV PC,LR style returns):
            // don't apply the getReg() PC+8 read-side bias to this write.
            r_[ARM_PC] = result & ~1u; // simplistic: no full CPSR-restore-from-SPSR modes supported
            return ArmFault::None;
        }
    }

    r_[ARM_PC] += 4;
    return ArmFault::None;
}

ArmFault ArmCpu::execBranch(uint32_t insn) {
    bool link = bit(insn, 24);
    uint32_t imm24 = bits(insn, 23, 0);
    int32_t offset = signExtend(imm24, 24) << 2;
    uint32_t pc = r_[ARM_PC];
    uint32_t target = static_cast<uint32_t>(pc + 8 + offset);
    if (link) {
        r_[ARM_LR] = pc + 4;
    }
    r_[ARM_PC] = target;
    return ArmFault::None;
}

ArmFault ArmCpu::execBranchExchange(uint32_t insn) {
    uint32_t rm = bits(insn, 3, 0);
    uint32_t target = getReg(rm);
    bool toThumb = (target & 1u) != 0;
    cpsr_ = toThumb ? (cpsr_ | (1u << CPSR_BIT_T)) : (cpsr_ & ~(1u << CPSR_BIT_T));
    r_[ARM_PC] = target & ~1u;
    return ArmFault::None;
}

ArmFault ArmCpu::execSingleDataTransfer(uint32_t insn) {
    bool immediateOffset = !bit(insn, 25); // note: inverted vs data-processing's bit25 meaning
    bool preIndex = bit(insn, 24);
    bool add = bit(insn, 23);
    bool byteTransfer = bit(insn, 22);
    bool writeback = bit(insn, 21);
    bool isLoad = bit(insn, 20);
    uint32_t rn = bits(insn, 19, 16);
    uint32_t rd = bits(insn, 15, 12);

    uint32_t offset;
    if (immediateOffset) {
        offset = bits(insn, 11, 0);
    } else {
        // Register offset, optionally shifted (same shifter as data-processing,
        // but always shift-by-immediate here - bit4 is always 0 in this form).
        Shifted s = shifterOperand(insn, false);
        offset = s.value;
    }

    uint32_t base = getReg(rn);
    uint32_t effectiveAddress = add ? (base + offset) : (base - offset);
    uint32_t transferAddress = preIndex ? effectiveAddress : base;

    ArmFault fault = ArmFault::None;
    if (isLoad) {
        uint8_t buf[4] = {0, 0, 0, 0};
        size_t len = byteTransfer ? 1 : 4;
        if (!doRead(transferAddress, buf, len, &fault)) return fault;
        uint32_t value = byteTransfer ? buf[0]
            : (static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
               (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24));
        setReg(rd, value);
    } else {
        uint32_t value = getReg(rd);
        uint8_t buf[4] = {
            static_cast<uint8_t>(value & 0xFF),
            static_cast<uint8_t>((value >> 8) & 0xFF),
            static_cast<uint8_t>((value >> 16) & 0xFF),
            static_cast<uint8_t>((value >> 24) & 0xFF)
        };
        size_t len = byteTransfer ? 1 : 4;
        if (!doWrite(transferAddress, buf, len, &fault)) return fault;
    }

    if (!preIndex || writeback) {
        setReg(rn, effectiveAddress);
    }

    if (!(isLoad && rd == ARM_PC)) {
        r_[ARM_PC] += 4;
    }
    return ArmFault::None;
}

ArmFault ArmCpu::execHalfwordSignedTransfer(uint32_t insn) {
    bool preIndex = bit(insn, 24);
    bool add = bit(insn, 23);
    bool immediateOffset = bit(insn, 22);
    bool writeback = bit(insn, 21);
    bool isLoad = bit(insn, 20);
    uint32_t rn = bits(insn, 19, 16);
    uint32_t rd = bits(insn, 15, 12);
    uint32_t sh = bits(insn, 6, 5); // 01=unsigned halfword, 10=signed byte, 11=signed halfword

    uint32_t offset;
    if (immediateOffset) {
        offset = (bits(insn, 11, 8) << 4) | bits(insn, 3, 0);
    } else {
        offset = getReg(bits(insn, 3, 0));
    }

    uint32_t base = getReg(rn);
    uint32_t effectiveAddress = add ? (base + offset) : (base - offset);
    uint32_t transferAddress = preIndex ? effectiveAddress : base;

    ArmFault fault = ArmFault::None;
    if (isLoad) {
        uint32_t value = 0;
        if (sh == 0b01) { // unsigned halfword
            uint8_t buf[2];
            if (!doRead(transferAddress, buf, 2, &fault)) return fault;
            value = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8);
        } else if (sh == 0b10) { // signed byte
            uint8_t buf[1];
            if (!doRead(transferAddress, buf, 1, &fault)) return fault;
            value = static_cast<uint32_t>(signExtend(buf[0], 8));
        } else { // 0b11 signed halfword
            uint8_t buf[2];
            if (!doRead(transferAddress, buf, 2, &fault)) return fault;
            uint32_t h = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8);
            value = static_cast<uint32_t>(signExtend(h, 16));
        }
        setReg(rd, value);
    } else {
        // STRH only (signed store forms aren't defined by the ISA)
        uint32_t value = getReg(rd);
        uint8_t buf[2] = { static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF) };
        if (!doWrite(transferAddress, buf, 2, &fault)) return fault;
    }

    if (!preIndex || writeback) {
        setReg(rn, effectiveAddress);
    }
    r_[ARM_PC] += 4;
    return ArmFault::None;
}

ArmFault ArmCpu::execBlockDataTransfer(uint32_t insn) {
    bool preIndex = bit(insn, 24);
    bool add = bit(insn, 23);
    bool writeback = bit(insn, 21);
    bool isLoad = bit(insn, 20);
    uint32_t rn = bits(insn, 19, 16);
    uint32_t regList = bits(insn, 15, 0);

    int count = 0;
    for (int i = 0; i < 16; i++) if (bit(regList, i)) count++;

    uint32_t base = getReg(rn);
    uint32_t startAddress = add ? base : (base - static_cast<uint32_t>(count) * 4);
    if (!add) startAddress += 4; // DA/DB math below adjusts per-transfer instead; simpler: recompute directly
    // Recompute precisely per addressing mode to avoid off-by-one drift:
    uint32_t address;
    if (add && preIndex) address = base + 4;               // IB
    else if (add && !preIndex) address = base;              // IA
    else if (!add && preIndex) address = base - static_cast<uint32_t>(count) * 4; // DB
    else address = base - static_cast<uint32_t>(count) * 4 + 4;                    // DA

    ArmFault fault = ArmFault::None;
    for (int i = 0; i < 16; i++) {
        if (!bit(regList, i)) continue;
        if (isLoad) {
            uint8_t buf[4];
            if (!doRead(address, buf, 4, &fault)) return fault;
            uint32_t value = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                              (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
            if (i == ARM_PC) r_[ARM_PC] = value & ~1u; else r_[i] = value;
        } else {
            uint32_t value = getReg(i);
            uint8_t buf[4] = {
                static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
            };
            if (!doWrite(address, buf, 4, &fault)) return fault;
        }
        address += 4;
    }

    if (writeback) {
        uint32_t newBase = add ? (base + static_cast<uint32_t>(count) * 4)
                                : (base - static_cast<uint32_t>(count) * 4);
        setReg(rn, newBase);
    }

    if (!(isLoad && bit(regList, ARM_PC))) {
        r_[ARM_PC] += 4;
    }
    return ArmFault::None;
}

ArmFault ArmCpu::execMultiply(uint32_t insn) {
    bool accumulate = bit(insn, 21);
    bool setFlags = bit(insn, 20);
    uint32_t rd = bits(insn, 19, 16);
    uint32_t rn = bits(insn, 15, 12);
    uint32_t rs = bits(insn, 11, 8);
    uint32_t rm = bits(insn, 3, 0);

    uint32_t result = getReg(rm) * getReg(rs);
    if (accumulate) result += getReg(rn);
    setReg(rd, result);
    if (setFlags) setNZ(result);
    r_[ARM_PC] += 4;
    return ArmFault::None;
}

// ---------------------------------------------------------------------------
// Thumb (16-bit) decode/execute
// ---------------------------------------------------------------------------

ArmFault ArmCpu::stepThumb() {
    uint32_t pc = r_[ARM_PC];
    if (pc & 0x1) return ArmFault::InvalidInsn;

    uint8_t raw[2];
    ArmFault fault = ArmFault::None;
    if (!doFetch(pc, raw, 2, &fault)) return fault;
    uint16_t insn = static_cast<uint16_t>(raw[0]) | (static_cast<uint16_t>(raw[1]) << 8);
    return execThumb(insn);
}

ArmFault ArmCpu::execThumb(uint16_t insn) {
    uint32_t pc = r_[ARM_PC];

    // Format 19: long branch with link - BL, two consecutive halfwords.
    // High half: 11110 offset11 (sets LR = PC+4+(offset<<12), signed)
    // Low half:  11111 offset11 (target = LR + (offset<<1); LR = next|1)
    if (bits(insn, 15, 11) == 0b11110) {
        uint32_t offsetHigh = bits(insn, 10, 0);
        int32_t signedHigh = signExtend(offsetHigh, 11);
        r_[ARM_LR] = static_cast<uint32_t>(pc + 4 + (signedHigh << 12));
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }
    if (bits(insn, 15, 11) == 0b11111) {
        uint32_t offsetLow = bits(insn, 10, 0);
        uint32_t target = r_[ARM_LR] + (offsetLow << 1);
        r_[ARM_LR] = (pc + 2) | 1u;
        r_[ARM_PC] = target;
        return ArmFault::None;
    }

    // Format 18: unconditional branch - bits15:11 = 11100
    if (bits(insn, 15, 11) == 0b11100) {
        uint32_t offset11 = bits(insn, 10, 0);
        int32_t signedOffset = signExtend(offset11, 11) << 1;
        r_[ARM_PC] = static_cast<uint32_t>(pc + 4 + signedOffset);
        return ArmFault::None;
    }

    // Format 17: software interrupt - bits15:8 = 11011111. Not used by any
    // real MRE-era call path we support (see vm_dispatch_bridge.cpp's
    // fetch-unmapped trap instead) - skip over it rather than fault, in
    // case a real binary uses it for something we don't need to emulate.
    if (bits(insn, 15, 8) == 0b11011111) {
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 16: conditional branch - bits15:12 = 1101, cond = bits11:8
    if (bits(insn, 15, 12) == 0b1101) {
        uint32_t cond = bits(insn, 11, 8);
        if (cond == 0xF) return ArmFault::InvalidInsn; // reserved (SWI handled above)
        if (!conditionPassed(cond)) { r_[ARM_PC] = pc + 2; return ArmFault::None; }
        uint32_t offset8 = bits(insn, 7, 0);
        int32_t signedOffset = signExtend(offset8, 8) << 1;
        r_[ARM_PC] = static_cast<uint32_t>(pc + 4 + signedOffset);
        return ArmFault::None;
    }

    // Format 15: multiple load/store - bits15:12 = 1100
    if (bits(insn, 15, 12) == 0b1100) {
        bool isLoad = bit(insn, 11);
        uint32_t rb = bits(insn, 10, 8);
        uint32_t regList = bits(insn, 7, 0);
        uint32_t address = r_[rb];
        ArmFault fault = ArmFault::None;
        for (int i = 0; i < 8; i++) {
            if (!bit(regList, i)) continue;
            if (isLoad) {
                uint8_t buf[4];
                if (!doRead(address, buf, 4, &fault)) return fault;
                r_[i] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                        (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
            } else {
                uint32_t value = r_[i];
                uint8_t buf[4] = {
                    static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                    static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
                };
                if (!doWrite(address, buf, 4, &fault)) return fault;
            }
            address += 4;
        }
        r_[rb] = address; // Thumb LDM/STM always writes back
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 14: push/pop - bits15:12 = 1011, bits10:9 = 10
    if (bits(insn, 15, 12) == 0b1011 && bits(insn, 10, 9) == 0b10) {
        bool isPop = bit(insn, 11);
        bool includePcLr = bit(insn, 8);
        uint32_t regList = bits(insn, 7, 0);
        ArmFault fault = ArmFault::None;
        if (isPop) {
            uint32_t address = r_[ARM_SP];
            for (int i = 0; i < 8; i++) {
                if (!bit(regList, i)) continue;
                uint8_t buf[4];
                if (!doRead(address, buf, 4, &fault)) return fault;
                r_[i] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                        (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
                address += 4;
            }
            if (includePcLr) {
                uint8_t buf[4];
                if (!doRead(address, buf, 4, &fault)) return fault;
                uint32_t value = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                                  (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
                r_[ARM_PC] = value & ~1u;
                address += 4;
            }
            r_[ARM_SP] = address;
            if (!includePcLr) r_[ARM_PC] = pc + 2;
            return ArmFault::None;
        } else {
            int count = 0;
            for (int i = 0; i < 8; i++) if (bit(regList, i)) count++;
            if (includePcLr) count++;
            uint32_t address = r_[ARM_SP] - static_cast<uint32_t>(count) * 4;
            uint32_t writeAddr = address;
            for (int i = 0; i < 8; i++) {
                if (!bit(regList, i)) continue;
                uint32_t value = r_[i];
                uint8_t buf[4] = {
                    static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                    static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
                };
                if (!doWrite(writeAddr, buf, 4, &fault)) return fault;
                writeAddr += 4;
            }
            if (includePcLr) {
                uint32_t value = r_[ARM_LR];
                uint8_t buf[4] = {
                    static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                    static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
                };
                if (!doWrite(writeAddr, buf, 4, &fault)) return fault;
            }
            r_[ARM_SP] = address;
            r_[ARM_PC] = pc + 2;
            return ArmFault::None;
        }
    }

    // Format 13: add offset to SP - bits15:8 = 10110000
    if (bits(insn, 15, 8) == 0b10110000) {
        uint32_t imm7 = bits(insn, 6, 0);
        bool negative = bit(insn, 7);
        uint32_t offset = imm7 << 2;
        r_[ARM_SP] = negative ? (r_[ARM_SP] - offset) : (r_[ARM_SP] + offset);
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 12: load address - bits15:12 = 1010 ; ADD Rd, (PC|SP), #imm8*4
    if (bits(insn, 15, 12) == 0b1010) {
        bool useSp = bit(insn, 11);
        uint32_t rd = bits(insn, 10, 8);
        uint32_t imm8 = bits(insn, 7, 0);
        uint32_t base = useSp ? r_[ARM_SP] : ((pc + 4) & ~3u);
        r_[rd] = base + (imm8 << 2);
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 11: SP-relative load/store - bits15:12 = 1001
    if (bits(insn, 15, 12) == 0b1001) {
        bool isLoad = bit(insn, 11);
        uint32_t rd = bits(insn, 10, 8);
        uint32_t imm8 = bits(insn, 7, 0);
        uint32_t address = r_[ARM_SP] + (imm8 << 2);
        ArmFault fault = ArmFault::None;
        if (isLoad) {
            uint8_t buf[4];
            if (!doRead(address, buf, 4, &fault)) return fault;
            r_[rd] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                     (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
        } else {
            uint32_t value = r_[rd];
            uint8_t buf[4] = {
                static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
            };
            if (!doWrite(address, buf, 4, &fault)) return fault;
        }
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 10: load/store halfword - bits15:12 = 1000
    if (bits(insn, 15, 12) == 0b1000) {
        bool isLoad = bit(insn, 11);
        uint32_t imm5 = bits(insn, 10, 6);
        uint32_t rb = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t address = r_[rb] + (imm5 << 1);
        ArmFault fault = ArmFault::None;
        if (isLoad) {
            uint8_t buf[2];
            if (!doRead(address, buf, 2, &fault)) return fault;
            r_[rd] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8);
        } else {
            uint32_t value = r_[rd];
            uint8_t buf[2] = { static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF) };
            if (!doWrite(address, buf, 2, &fault)) return fault;
        }
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 9: load/store with immediate offset - bits15:13 = 011
    if (bits(insn, 15, 13) == 0b011) {
        bool byteTransfer = bit(insn, 12);
        bool isLoad = bit(insn, 11);
        uint32_t imm5 = bits(insn, 10, 6);
        uint32_t rb = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t offset = byteTransfer ? imm5 : (imm5 << 2);
        uint32_t address = r_[rb] + offset;
        ArmFault fault = ArmFault::None;
        if (isLoad) {
            size_t len = byteTransfer ? 1 : 4;
            uint8_t buf[4] = {0, 0, 0, 0};
            if (!doRead(address, buf, len, &fault)) return fault;
            r_[rd] = byteTransfer ? buf[0]
                : (static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                   (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24));
        } else {
            uint32_t value = r_[rd];
            uint8_t buf[4] = {
                static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
            };
            size_t len = byteTransfer ? 1 : 4;
            if (!doWrite(address, buf, len, &fault)) return fault;
        }
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Formats 7/8: load/store with register offset (plain and sign-extended) - bits15:12 = 0101
    if (bits(insn, 15, 12) == 0b0101) {
        uint32_t ro = bits(insn, 8, 6);
        uint32_t rb = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t address = r_[rb] + r_[ro];
        bool bitL = bit(insn, 11);
        bool bitB = bit(insn, 10);
        bool signExtended = bit(insn, 9);
        ArmFault fault = ArmFault::None;

        if (!signExtended) {
            // Format 7: STR/STRB/LDR/LDRB
            if (bitL) {
                size_t len = bitB ? 1 : 4;
                uint8_t buf[4] = {0, 0, 0, 0};
                if (!doRead(address, buf, len, &fault)) return fault;
                r_[rd] = bitB ? buf[0]
                    : (static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                       (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24));
            } else {
                uint32_t value = r_[rd];
                uint8_t buf[4] = {
                    static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF),
                    static_cast<uint8_t>((value >> 16) & 0xFF), static_cast<uint8_t>((value >> 24) & 0xFF)
                };
                size_t len = bitB ? 1 : 4;
                if (!doWrite(address, buf, len, &fault)) return fault;
            }
        } else {
            // Format 8: STRH/LDRH/LDSB/LDSH, selected by (bitB,bitL) = (H,S) pair semantics:
            // H=0,S=0 -> STRH ; H=0,S=1 -> LDSB ; H=1,S=0 -> LDRH ; H=1,S=1 -> LDSH
            // Here bit10 is "S" (sign-extend) and bit11 is "H" per the ISA table.
            bool hFlag = bitL;
            bool sFlag = bitB;
            if (!hFlag && !sFlag) { // STRH
                uint32_t value = r_[rd];
                uint8_t buf[2] = { static_cast<uint8_t>(value & 0xFF), static_cast<uint8_t>((value >> 8) & 0xFF) };
                if (!doWrite(address, buf, 2, &fault)) return fault;
            } else if (!hFlag && sFlag) { // LDSB
                uint8_t buf[1];
                if (!doRead(address, buf, 1, &fault)) return fault;
                r_[rd] = static_cast<uint32_t>(signExtend(buf[0], 8));
            } else if (hFlag && !sFlag) { // LDRH
                uint8_t buf[2];
                if (!doRead(address, buf, 2, &fault)) return fault;
                r_[rd] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8);
            } else { // LDSH
                uint8_t buf[2];
                if (!doRead(address, buf, 2, &fault)) return fault;
                uint32_t h = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8);
                r_[rd] = static_cast<uint32_t>(signExtend(h, 16));
            }
        }
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 6: PC-relative load - bits15:11 = 01001
    if (bits(insn, 15, 11) == 0b01001) {
        uint32_t rd = bits(insn, 10, 8);
        uint32_t imm8 = bits(insn, 7, 0);
        uint32_t address = ((pc + 4) & ~3u) + (imm8 << 2);
        uint8_t buf[4];
        ArmFault fault = ArmFault::None;
        if (!doRead(address, buf, 4, &fault)) return fault;
        r_[rd] = static_cast<uint32_t>(buf[0]) | (static_cast<uint32_t>(buf[1]) << 8) |
                 (static_cast<uint32_t>(buf[2]) << 16) | (static_cast<uint32_t>(buf[3]) << 24);
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 5: hi register operations / BX - bits15:10 = 010001
    if (bits(insn, 15, 10) == 0b010001) {
        uint32_t op = bits(insn, 9, 8);
        bool h1 = bit(insn, 7);
        bool h2 = bit(insn, 6);
        uint32_t rs = bits(insn, 5, 3) | (h2 ? 8 : 0);
        uint32_t rd = bits(insn, 2, 0) | (h1 ? 8 : 0);

        if (op == 0b11) { // BX (and BLX-by-register isn't in classic ARMv4T Thumb; BX only)
            uint32_t target = r_[rs];
            bool toThumb = (target & 1u) != 0;
            cpsr_ = toThumb ? (cpsr_ | (1u << CPSR_BIT_T)) : (cpsr_ & ~(1u << CPSR_BIT_T));
            r_[ARM_PC] = target & ~1u;
            return ArmFault::None;
        }

        uint32_t rsVal = getReg(rs);
        uint32_t rdVal = getReg(rd);
        switch (op) {
            case 0b00: { // ADD
                uint32_t result = rdVal + rsVal;
                r_[rd] = result;
                break;
            }
            case 0b01: { // CMP - sets flags, doesn't write
                uint32_t result = rdVal - rsVal;
                setNZCV_sub(rdVal, rsVal, result);
                break;
            }
            case 0b10: { // MOV
                r_[rd] = rsVal;
                break;
            }
            default: break;
        }
        if (rd == ARM_PC && op != 0b01) {
            r_[ARM_PC] &= ~1u;
        } else {
            r_[ARM_PC] = pc + 2;
        }
        return ArmFault::None;
    }

    // Format 4: ALU operations - bits15:10 = 010000
    if (bits(insn, 15, 10) == 0b010000) {
        uint32_t op = bits(insn, 9, 6);
        uint32_t rs = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t rdVal = r_[rd];
        uint32_t rsVal = r_[rs];
        uint32_t result = 0;
        bool writesRd = true;

        switch (op) {
            case 0x0: result = rdVal & rsVal; break;                    // AND
            case 0x1: result = rdVal ^ rsVal; break;                    // EOR
            case 0x2: { // LSL by register
                uint32_t amt = rsVal & 0xFF;
                if (amt == 0) result = rdVal;
                else if (amt < 32) { cpsr_ = bit(rdVal, 32 - (int)amt) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = rdVal << amt; }
                else if (amt == 32) { cpsr_ = bit(rdVal,0) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = 0; }
                else { cpsr_ &= ~(1u<<CPSR_BIT_C); result = 0; }
                break;
            }
            case 0x3: { // LSR by register
                uint32_t amt = rsVal & 0xFF;
                if (amt == 0) result = rdVal;
                else if (amt < 32) { cpsr_ = bit(rdVal,(int)amt-1) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = rdVal >> amt; }
                else if (amt == 32) { cpsr_ = bit(rdVal,31) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = 0; }
                else { cpsr_ &= ~(1u<<CPSR_BIT_C); result = 0; }
                break;
            }
            case 0x4: { // ASR by register
                uint32_t amt = rsVal & 0xFF;
                int32_t signedVal = static_cast<int32_t>(rdVal);
                if (amt == 0) result = rdVal;
                else if (amt < 32) { cpsr_ = bit(rdVal,(int)amt-1) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = static_cast<uint32_t>(signedVal >> amt); }
                else { bool s = bit(rdVal,31); cpsr_ = s ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = s ? 0xFFFFFFFFu : 0u; }
                break;
            }
            case 0x5: { // ADC
                uint64_t sum = static_cast<uint64_t>(rdVal) + rsVal + (bit(cpsr_, CPSR_BIT_C) ? 1 : 0);
                result = static_cast<uint32_t>(sum);
                setNZCV_add(rdVal, rsVal, result);
                setReg(rd, result);
                r_[ARM_PC] = pc + 2;
                return ArmFault::None;
            }
            case 0x6: { // SBC
                uint64_t diff = static_cast<uint64_t>(rdVal) - rsVal - (bit(cpsr_, CPSR_BIT_C) ? 0 : 1);
                result = static_cast<uint32_t>(diff);
                setNZCV_sub(rdVal, rsVal, result);
                setReg(rd, result);
                r_[ARM_PC] = pc + 2;
                return ArmFault::None;
            }
            case 0x7: { // ROR by register
                uint32_t amt = rsVal & 0xFF;
                uint32_t rot = amt & 31u;
                if (amt == 0) result = rdVal;
                else if (rot == 0) { cpsr_ = bit(rdVal,31) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = rdVal; }
                else { cpsr_ = bit(rdVal,(int)rot-1) ? (cpsr_ | (1u<<CPSR_BIT_C)) : (cpsr_ & ~(1u<<CPSR_BIT_C)); result = rorImm(rdVal, rot); }
                break;
            }
            case 0x8: result = rdVal & rsVal; writesRd = false; break;  // TST
            case 0x9: { // NEG
                result = 0u - rsVal;
                setNZCV_sub(0u, rsVal, result);
                setReg(rd, result);
                r_[ARM_PC] = pc + 2;
                return ArmFault::None;
            }
            case 0xA: { // CMP
                result = rdVal - rsVal;
                setNZCV_sub(rdVal, rsVal, result);
                writesRd = false;
                break;
            }
            case 0xB: { // CMN
                result = rdVal + rsVal;
                setNZCV_add(rdVal, rsVal, result);
                writesRd = false;
                break;
            }
            case 0xC: result = rdVal | rsVal; break;                    // ORR
            case 0xD: result = rdVal * rsVal; break;                    // MUL
            case 0xE: result = rdVal & ~rsVal; break;                   // BIC
            case 0xF: result = ~rsVal; break;                           // MVN
            default: return ArmFault::InvalidInsn;
        }

        // Ops that didn't already set flags/return above (logical family): N,Z only.
        if (op <= 0x1 || op == 0x7 || (op >= 0xC && op != 0xD) || op == 0xD || op == 0x8 || op == 0xF) {
            setNZ(result);
        }
        if (writesRd) setReg(rd, result);
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 3: move/compare/add/subtract immediate - bits15:13 = 001
    if (bits(insn, 15, 13) == 0b001) {
        uint32_t op = bits(insn, 12, 11);
        uint32_t rd = bits(insn, 10, 8);
        uint32_t imm8 = bits(insn, 7, 0);
        uint32_t rdVal = r_[rd];
        switch (op) {
            case 0b00: { // MOV
                r_[rd] = imm8;
                setNZ(imm8);
                break;
            }
            case 0b01: { // CMP
                uint32_t result = rdVal - imm8;
                setNZCV_sub(rdVal, imm8, result);
                break;
            }
            case 0b10: { // ADD
                uint32_t result = rdVal + imm8;
                setNZCV_add(rdVal, imm8, result);
                r_[rd] = result;
                break;
            }
            default: { // SUB
                uint32_t result = rdVal - imm8;
                setNZCV_sub(rdVal, imm8, result);
                r_[rd] = result;
                break;
            }
        }
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 2: add/subtract register or 3-bit immediate - bits15:11 = 00011
    if (bits(insn, 15, 11) == 0b00011) {
        bool immediateFlag = bit(insn, 10);
        bool isSub = bit(insn, 9);
        uint32_t rnOrImm3 = bits(insn, 8, 6);
        uint32_t rs = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t rsVal = r_[rs];
        uint32_t operand = immediateFlag ? rnOrImm3 : r_[rnOrImm3];
        uint32_t result = isSub ? (rsVal - operand) : (rsVal + operand);
        if (isSub) setNZCV_sub(rsVal, operand, result); else setNZCV_add(rsVal, operand, result);
        r_[rd] = result;
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    // Format 1: move shifted register - bits15:13 = 000 (and not format 2's 00011 prefix)
    if (bits(insn, 15, 13) == 0b000) {
        uint32_t op = bits(insn, 12, 11);
        uint32_t imm5 = bits(insn, 10, 6);
        uint32_t rs = bits(insn, 5, 3);
        uint32_t rd = bits(insn, 2, 0);
        uint32_t rsVal = r_[rs];
        uint32_t result;
        bool carryOut = bit(cpsr_, CPSR_BIT_C);

        switch (op) {
            case 0b00: // LSL
                if (imm5 == 0) { result = rsVal; }
                else { carryOut = bit(rsVal, 32 - (int)imm5); result = rsVal << imm5; }
                break;
            case 0b01: // LSR
                if (imm5 == 0) { carryOut = bit(rsVal, 31); result = 0; }
                else { carryOut = bit(rsVal, (int)imm5 - 1); result = rsVal >> imm5; }
                break;
            default: { // ASR (op==0b10); op==0b11 is format 2, already handled above
                int32_t signedVal = static_cast<int32_t>(rsVal);
                if (imm5 == 0) { carryOut = bit(rsVal, 31); result = static_cast<uint32_t>(signedVal >> 31); }
                else { carryOut = bit(rsVal, (int)imm5 - 1); result = static_cast<uint32_t>(signedVal >> imm5); }
                break;
            }
        }
        cpsr_ = carryOut ? (cpsr_ | (1u << CPSR_BIT_C)) : (cpsr_ & ~(1u << CPSR_BIT_C));
        setNZ(result);
        r_[rd] = result;
        r_[ARM_PC] = pc + 2;
        return ArmFault::None;
    }

    return ArmFault::InvalidInsn;
}
