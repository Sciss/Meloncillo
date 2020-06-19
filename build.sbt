lazy val commonSettings = Seq(
  name             := "Meloncillo",
  version          := "1.0.0-SNAPSHOT",
  organization     := "de.sciss",
  description      := "An application to compose spatial sound",
  homepage         := Some(url(s"https://github.com/Sciss/${name.value}")),
  licenses         := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt")),
  scalaVersion     := "2.12.11",
  crossPaths       := false,  // this is just a Java project right now!
  autoScalaLibrary := false,
  mainClass        := Some("de.sciss.meloncillo.Main"),
)

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(publishSettings)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := Some(
    if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}
)

