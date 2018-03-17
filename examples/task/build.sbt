lazy val root = (project in file("."))
  .dependsOn(pureApp)
  .settings(
    scalaVersion := "2.12.4",
    name := "pureapp-monix-task-example",
    libraryDependencies += "io.monix" % "monix_2.12" % "3.0.0-M3"
  )

lazy val pureApp = ProjectRef(file("../.."), "com/github/battermann/pureapp")
