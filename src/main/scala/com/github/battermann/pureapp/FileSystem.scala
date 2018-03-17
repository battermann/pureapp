package com.github.battermann.pureapp

import java.io.{File, PrintWriter}

import cats.effect.IO

object FileSystem {
  def readLines(name: String): IO[Either[Throwable, List[String]]] =
    IO {
      io.Source.fromFile(name).getLines.toList
    }.attempt

  def save(name: String, content: String): IO[Either[Throwable, Unit]] =
    IO {
      val writer = new PrintWriter(new File(name))
      writer.write(content)
      writer.close()
    }.attempt
}
