lazy val root = (project in file(".")).
  settings(
    name := "image-processor",
    organization := "com.blinkbox.books.marvin",
    version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
    scalaVersion := "2.11.4",
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7", "-Xfatal-warnings", "-Xfuture"),
    libraryDependencies ++= {
      val akkaV = "2.3.8"
      Seq(
        "com.blinkbox.books"               %% "common-config"      % "2.3.1",
        "com.blinkbox.books"               %% "common-scala-test"  % "0.3.0"  % Test,
        "com.blinkbox.books.hermes"        %% "rabbitmq-ha"        % "8.1.1",
        "com.blinkbox.books.hermes"        %% "message-schemas"    % "0.7.3",
        "com.blinkbox.books.quartermaster" %% "common-mapping"     % "0.2.0",
        "com.typesafe.akka"                %% "akka-testkit"       % akkaV    % Test,
        "com.mortennobel"                  %  "java-image-scaling" % "0.8.5",
        "com.jsuereth"                     %% "scala-arm"          % "1.4",
        "org.apache.commons"               %  "commons-lang3"      % "3.3.2",
        "org.imgscalr"                     %  "imgscalr-lib"       % "4.2"
      )
    }
  ).
  settings(rpmPrepSettings: _*)
