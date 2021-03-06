package com.github.battermann.pureapp.interpreters

import cats.effect.IO

object Terminal {
  def putStrLn(line: String): IO[Unit] =
    IO { println(line) }

  def putStr(str: String): IO[Unit] =
    IO { print(str) }

  def readLine: IO[String] =
    IO { io.StdIn.readLine() }
}
