package com.carlosedp.riscvassembler

import scala.collection.mutable.ArrayBuffer
import scala.io.Source

object RISCVAssembler {

  /** Generate an hex string output fom the assembly source file
    *
    * Usage:
    *
    * {{{
    * val outputHex = RISCVAssembler.fromFile("input.asm")
    * }}}
    *
    * @param fileName
    *   the assembly source file
    * @return
    *   the output hex string
    */
  def fromFile(filename: String): String =
    fromString(Source.fromFile(filename).getLines().mkString("\n"))

  /** Generate an hex string output fom the assembly string
    *
    * Usage:
    *
    * {{{
    * val input =
    *       """
    *       addi x1 , x0,   1000
    *       addi x2 , x1,   2000
    *       addi x3 , x2,  -1000
    *       addi x4 , x3,  -2000
    *       addi x5 , x4,   1000
    *       """.stripMargin
    *     val outputHex = RISCVAssembler.fromString(input)
    * }}}
    *
    * @param input
    *   input assembly string to assemble (multiline string)
    * @return
    *   the assembled hex string
    */
  def fromString(input: String): String = {
    val (instructions, addresses, labels) = parseLines(input)
    (instructions zip addresses).map { case (i: String, a: String) => binOutput(i, a, labels) }
      .map(GenHex(_))
      .mkString("\n")
  }

  /** Parses input string lines to generate the list of instructions, addresses and label addresses
    *
    * @param input
    *   input multiline assembly string
    * @return
    *   a tuple containing:
    *   - `ArrayBuffer[String]` with the assembly instruction
    *   - `ArrayBuffer[String]` with the assembly instruction address
    *   - `Map[String, String]` with the assembly label addresses
    */
  def parseLines(input: String): (ArrayBuffer[String], ArrayBuffer[String], Map[String, String]) = {
    val instList = input.split("\n").toList.filter(_.nonEmpty).filter(!_.trim().isEmpty()).map(_.trim)
    val ignores  = Seq(".", "/")
    // println("--- Instruction list:")
    // println(instList.mkString("\n"))

    // Filter lines which begin with characters from `ignores`
    val instListFilter = instList.filterNot(l => ignores.contains(l.trim().take(1))).toIndexedSeq
    // println("--- Instruction list filtered:")
    // println(instListFilter.mkString("\n"))

    var idx              = 0
    val instructions     = scala.collection.mutable.ArrayBuffer.empty[String]
    val instructionsAddr = scala.collection.mutable.ArrayBuffer.empty[String]
    val labelIndex       = scala.collection.mutable.Map[String, String]()

    instListFilter.foreach { data =>
      // That's an ugly parser, but works for now :)
      // println(s"-- Processing line: $data, address: ${(idx * 4L).toHexString}")
      val hasLabel = data.indexOf(":")
      if (hasLabel != -1) {
        if (""".+:\s*(\/.*)?$""".r.findFirstIn(data).isDefined) {
          // println(s"Has only label: $data")
          // Has label without code, this label points to next address
          labelIndex(data.split(":")(0).replace(":", "")) = ((idx + 1) * 4L).toHexString
          idx += 1
        } else {
          // println(s"Has label and data: $data")
          // Has label and code in the same line, this label points to this address
          // """^.+:\s+[^\/\*]+$"""
          labelIndex(data.split(':')(0).replace(":", "").trim) = (idx * 4L).toHexString
          instructions.append(data.split(':')(1).trim)
          instructionsAddr.append((idx * 4L).toHexString)
          idx += 1
        }
      } else {
        // println(s"Has only data: $data")
        instructions.append(data.trim)
        instructionsAddr.append((idx * 4L).toHexString)
        idx += 1
      }
    }
    // println(s"Instructions: $instructions")
    // println(s"Instruction addresses: $instructionsAddr")
    // println(s"Label indexes: $labelIndex")
    (instructions, instructionsAddr, labelIndex.toMap)
  }

  /** Generate the binary output for the input instruction
    * @param input
    *   the input instruction (eg. "add x1, x2, x3")
    * @return
    *   the binary output in string format
    */
  def binOutput(
    instruction: String,
    address:     String = "0",
    labelIndex:  Map[String, String] = Map[String, String](),
    width:       Int = 32
  ): String = {
    val (op, opdata) = InstructionParser(instruction, address, labelIndex)
    FillInstruction(op, opdata).takeRight(width)
  }
}