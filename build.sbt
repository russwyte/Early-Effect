import java.nio.file.Files

enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)

val cachedAssets = taskKey[Seq[String]]("the list of files scala.js bundler produces via webpack")

val copyWorker = taskKey[Unit]("Moves worker.js to public")

val diodeVersion = "1.1.14"

val token = sys.env.getOrElse("GITHUB_TOKEN", "No Token")

lazy val root = project
  .in(file("."))
  .aggregate(core, demo, demoModel, demoWorker)
  .settings(
    scalaVersion := "2.13.5",
    name := "root",
    publish := {},
    publishLocal := {},
  )

val baseSettings = Seq(
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  version := "0.5.1",
  scalaVersion := "2.13.5",
  organization := "rocks.earlyeffect",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
  libraryDependencies ++= Seq(
    "io.suzaku"     %%% "diode"       % diodeVersion,
    "org.scala-js"  %%% "scalajs-dom" % "1.1.0",
    "org.scalatest" %%% "scalatest"   % "3.2.3" % Test,
  ),
  Test / requireJsDomEnv := true,
  installJsdom / version.withRank(KeyRanks.Invisible) := "16.4.0",
  scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  webpack / version.withRank(KeyRanks.Invisible) := "4.46.0",
  webpackCliVersion.withRank(KeyRanks.Invisible) := "3.3.11",
  startWebpackDevServer / version.withRank(KeyRanks.Invisible) := "3.11.2",
)

lazy val core = project
  .in(file("core"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(
    baseSettings,
    name := "core",
    Compile / fastOptJS / webpackEmitSourceMaps := true,
    Compile / fullOptJS / webpackEmitSourceMaps := false,
    Compile / npmDependencies ++= Seq(
      "preact"       -> "10.5.10",
      "autoprefixer" -> "10.2.3",
      "postcss"      -> "8.2.6",
    ),
    publishTo := Some("GitHub Package Registry".at("https://maven.pkg.github.com/russwyte/EarlyEffect")),
    credentials += Credentials("GitHub Package Registry", "maven.pkg.github.com", "russwyte", token),
  )

lazy val demoModel = project
  .in(file("demo-model"))
  .enablePlugins(ScalaJSPlugin)
  .disablePlugins(ScalaJSBundlerPlugin)
  .settings(
    baseSettings,
    name := "demo-model",
    publish := {},
    publishLocal := {},
    publish / skip := true,
    Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    libraryDependencies += "io.suzaku" %%% "diode" % diodeVersion,
    test := {},
  )

lazy val demoWorker = project
  .in(file("demo-worker"))
  .enablePlugins(ScalaJSPlugin, BuildInfoPlugin)
  .disablePlugins(ScalaJSBundlerPlugin)
  .settings(
    baseSettings,
    name := "demo-worker",
    publish := {},
    publishLocal := {},
    publish / skip := true,
    Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    test := {},
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, demo / cachedAssets),
    buildInfoPackage := "worker",
    mainClass.withRank(KeyRanks.Invisible) := Some("worker.ServiceWorker"),
    buildInfoOptions += BuildInfoOption.Traits("worker.Context"),
    Compile / fullOptJS / artifactPath := {
      (demo / Compile / webpack / fullOptJS / artifactPath).value.getParentFile / "dist/worker.js"
    },
    addCommandAlias("worker", "demoWorker/fullOptJS;demoWorker/copyWorker"),
    copyWorker := {
      val f    = (Compile / fullOptJS / artifactPath).value
      val path = ((demo / baseDirectory).value / "public/worker.js").toPath
      if (Files.exists(path)) Files.delete(path)
      Files.copy(f.toPath, path)
    },
  )

lazy val demo = project
  .in(file("demo-app"))
  .enablePlugins(ScalaJSBundlerPlugin)
  .dependsOn(core, demoModel % "test->test;compile->compile")
  .settings(
    baseSettings,
    name := "demo-app",
    // webpack stuff
    Compile / npmDependencies ++= Seq(
      "webpack-merge"       -> "4.1.2",
      "html-webpack-plugin" -> "4.5.2",
      "copy-webpack-plugin" -> "4.6.0",
      "crypto-js"           -> "4.0.0",
    ),
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    webpackResources := baseDirectory.value / "webpack" * "*",
    fastOptJS / webpackBundlingMode := BundlingMode.LibraryOnly(),
    fastOptJS / webpackConfigFile := Some(
      baseDirectory.value / "webpack" / "webpack-fastopt.config.js"
    ),
    fullOptJS / webpackConfigFile := Some(
      baseDirectory.value / "webpack" / "webpack-opt.config.js"
    ),
    Test / webpackConfigFile := Some(
      baseDirectory.value / "webpack" / "webpack-core.config.js"
    ),
    fastOptJS / webpackDevServerExtraArgs := Seq("--inline", "--hot"),
    //    webpackDevServerExtraArgs := Seq("--https", "--inline"),
//    version in startWebpackDevServer := "3.9.0",
    Compile / fastOptJS / webpackEmitSourceMaps := true,
    Compile / fullOptJS / webpackEmitSourceMaps := false,
    addCommandAlias(
      "demo",
      "demo/Compile/fastOptJS/startWebpackDevSer" +
        "ver;~demo/fastOptJS",
    ),
    addCommandAlias(
      "demoFull",
      "demo/Compile/fullOptJS/startWebpackDevServer;~demo/fullOptJS",
    ),
    test := {},
    publish / skip := true,
    publish := {},
    publishLocal := {},
    cachedAssets := {
      val files = (Compile / fullOptJS / webpack).value
      files
        .filter(!_.data.isHidden)
        .map { file =>
          file.data.getPath.split("/main/dist/")(1)
        }
    },
  )
