lazy val root = (project in file("."))
  .dependsOn(pureApp)
  .settings(
    scalaVersion := "2.12.4",
    name := "pureapp-monix-task-example",
    libraryDependencies += "io.monix" %% "monix" % "3.0.0-8084549"
  )

lazy val pureApp = ProjectRef(file("../.."), "pureapp")
