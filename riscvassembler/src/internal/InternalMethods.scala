package com.carlosedp.riscvassembler

import scala.util.Try

import ObjectUtils._

protected object InstructionParser {

  /** Parse an assembly instruction and return the opcode and opdata
    *
    * @param input
    *   the assembly instruction string
    * @param addr
    *   the assembly instruction address
    * @param labelIndex
    *   the index containing the label addresses
    * @return
    *   a tuple containing the Instruction and opdata
    */
  def apply(
    input:      String,
    addr:       String = "0",
    labelIndex: Map[String, String] = Map[String, String]()
  ): (Instruction, Map[String, Long]) = {
    // The regex splits the input into groups (dependind on type):
    // (0) - Instruction name
    // (1) - Instruction rd
    // (2) - Instruction rs1/imm
    // (3) - Instruction rs2/rs
    var instructionParts = input.trim.split("[\\s,\\(\\)]+").filter(_.nonEmpty)
    val inst             = Instructions(instructionParts(0))
    // Check here if it's a pseudo instruction
    if (inst.pseudo) {
      instructionParts = PseudoInstructions(instructionParts)
    }
    inst.instType match {
      case InstructionTypes.R =>
        (
          inst,
          Map(
            "rd"  -> RegMap(instructionParts(1)),
            "rs1" -> RegMap(instructionParts(2)),
            "rs2" -> RegMap(instructionParts(3))
          )
        )
      case InstructionTypes.I => {
        if (inst.hasOffset) {
          val imm =
            if (instructionParts(2).startsWith("0x")) BigInt(instructionParts(2).substring(2), 16).toLong
            else instructionParts(2).toLong
          (
            inst,
            Map(
              "rd"  -> RegMap(instructionParts(1)),
              "rs1" -> RegMap(instructionParts(3)),
              "imm" -> imm
            )
          )
        } else {
          val imm =
            if (instructionParts(3).startsWith("0x")) BigInt(instructionParts(3).substring(2), 16).toLong
            else instructionParts(3).toLong
          (
            inst,
            Map(
              "rd"  -> RegMap(instructionParts(1)),
              "rs1" -> RegMap(instructionParts(2)),
              "imm" -> imm
            )
          )
        }
      }
      case InstructionTypes.S => {
        val imm =
          if (instructionParts(2).startsWith("0x")) BigInt(instructionParts(2).substring(2), 16).toLong
          else instructionParts(2).toLong
        (
          inst,
          Map(
            "rs2" -> RegMap(instructionParts(1)),
            "rs1" -> RegMap(instructionParts(3)),
            "imm" -> imm
          )
        )
      }
      case InstructionTypes.B => {
        val imm = instructionParts(3) match {
          case i if i.startsWith("0x")      => BigInt(i.substring(2), 16).toLong
          case i if Try(i.toLong).isFailure => (BigInt(labelIndex(i), 16) - BigInt(addr, 16)).toLong
          case i                            => i.toLong
        }
        (
          inst,
          Map(
            "rs1" -> RegMap(instructionParts(1)),
            "rs2" -> RegMap(instructionParts(2)),
            "imm" -> imm
          )
        )
      }
      case InstructionTypes.U | InstructionTypes.J => {
        val imm = instructionParts(2) match {
          case i if i.startsWith("0x")      => BigInt(i.substring(2), 16).toLong
          case i if Try(i.toLong).isFailure => (BigInt(labelIndex(i), 16) - BigInt(addr, 16)).toLong
          case i                            => i.toLong
        }
        (inst, Map("rd" -> RegMap(instructionParts(1)), "imm" -> imm))
      }
    }
  }
}

protected object FillInstruction {

  /** Fills the instruction arguments based on instruction type
    *
    * @param op
    *   the instruction opcode and type
    * @param data
    *   the received instruction arguments
    * @return
    *   the filled instruction binary
    */
  def apply(op: Instruction, data: Map[String, Long]): String =
    op.instType match {
      case InstructionTypes.R => {
        val rd  = data("rd").toBinaryString.padZero(5)
        val rs1 = data("rs1").toBinaryString.padZero(5)
        val rs2 = data("rs2").toBinaryString.padZero(5)
        op.funct7 + rs2 + rs1 + op.funct3 + rd + op.opcode
      }

      case InstructionTypes.I => {
        val rd  = data("rd").toBinaryString.padZero(5)
        val rs1 = data("rs1").toBinaryString.padZero(5)
        val imm = data("imm").to32Bit.toBinaryString.padZero(12)
        imm + rs1 + op.funct3 + rd + op.opcode
      }

      case InstructionTypes.S => {
        val rs1 = data("rs1").toBinaryString.padZero(5)
        val rs2 = data("rs2").toBinaryString.padZero(5)
        val imm = data("imm").to32Bit.toBinaryString.padZero(12).reverse // reverse to have binary in little endian
        imm.slice(5, 12).reverse + rs2 + rs1 + op.funct3 + imm.slice(0, 5).reverse + op.opcode
      }

      case InstructionTypes.B => {
        val rs1 = data("rs1").toBinaryString.padZero(5)
        val rs2 = data("rs2").toBinaryString.padZero(5)
        val imm = data("imm").to32Bit.toBinaryString.padZero(13).reverse // reverse to have binary in little endian
        imm.slice(12, 13).reverse + imm.slice(5, 11).reverse + rs2 + rs1 + op.funct3 +
          imm.slice(1, 5).reverse + imm.slice(11, 12).reverse + op.opcode
      }

      case InstructionTypes.U => {
        val rd  = data("rd").toBinaryString.padZero(5)
        val imm = data("imm").to32Bit.toBinaryString.padZero(32).take(20)
        imm + rd + op.opcode
      }

      case InstructionTypes.J => {
        val rd  = data("rd").toBinaryString.padZero(5)
        val imm = data("imm").to32Bit.toBinaryString.padZero(21).reverse // reverse to have binary in little endian
        imm.slice(20, 21).reverse + imm.slice(1, 11).reverse + imm.slice(11, 12).reverse + imm
          .slice(12, 20)
          .reverse + rd + op.opcode
      }
    }
}

protected object GenHex {

  /** Generate the hex string of the instruction from binary
    *
    * @param input
    *   the binary string of the instruction
    * @return
    *   the hex string of the instruction
    */
  def apply(
    input: String
  ): String = {
    // Make this 64bit in the future
    val x = BigInt(input, 2).toLong
    f"0x$x%08X".toString.takeRight(8)
  }
}

protected object RegMap {

  /** Maps the register name or ABI name to the register number
    *
    * @param regName
    *   the register name
    * @return
    *   the register number
    */
  def apply(
    input: String
  ): Long =
    input match {
      case "x0" | "zero"      => 0
      case "x1" | "ra"        => 1
      case "x2" | "sp"        => 2
      case "x3" | "gp"        => 3
      case "x4" | "tp"        => 4
      case "x5" | "t0"        => 5
      case "x6" | "t1"        => 6
      case "x7" | "t2"        => 7
      case "x8" | "s0" | "fp" => 8
      case "x9" | "s1"        => 9
      case "x10" | "a0"       => 10
      case "x11" | "a1"       => 11
      case "x12" | "a2"       => 12
      case "x13" | "a3"       => 13
      case "x14" | "a4"       => 14
      case "x15" | "a5"       => 15
      case "x16" | "a6"       => 16
      case "x17" | "a7"       => 17
      case "x18" | "s2"       => 18
      case "x19" | "s3"       => 19
      case "x20" | "s4"       => 20
      case "x21" | "s5"       => 21
      case "x22" | "s6"       => 22
      case "x23" | "s7"       => 23
      case "x24" | "s8"       => 24
      case "x25" | "s9"       => 25
      case "x26" | "s10"      => 26
      case "x27" | "s11"      => 27
      case "x28" | "t3"       => 28
      case "x29" | "t4"       => 29
      case "x30" | "t5"       => 30
      case "x31" | "t6"       => 31
    }
}