lazy val root = (project in file(".")).
  settings(
    name := "image-processor",
    organization := "com.blinkbox.books.marvin",
    version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
    scalaVersion := "2.11.4",
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7", "-Xfatal-warnings", "-Xfuture"),
    libraryDependencies ++= {
      val akkaV = "2.3.7"
      Seq(
        "com.blinkbox.books"        %% "common-config"     % "2.3.0",
        "com.blinkbox.books"        %% "common-scala-test" % "0.3.0"  % Test,
        "com.blinkbox.books.hermes" %% "rabbitmq-ha"       % "8.1.1",
        "com.blinkbox.books.hermes" %% "message-schemas"   % "0.7.3",
        "com.typesafe.akka"         %% "akka-testkit"      % akkaV    % Test
      )
    }
  ).
  settings(rpmPrepSettings: _*)
