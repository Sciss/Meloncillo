import AssemblyKeys._

name := "Meloncillo"

version := "1.0.0-SNAPSHOT"

organization := "de.sciss"

description := "An application to compose spatial sound"

homepage := Some( url( "https://github.com/Sciss/Meloncillo" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.2"

crossPaths := false  // this is just a Java project right now!

retrieveManaged := true

mainClass := Some( "de.sciss.meloncillo.Main" )

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/Meloncillo.git</url>
  <connection>scm:git:git@github.com:Sciss/Meloncillo.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- packaging ----

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundle.settings: _* )

appbundle.icon := Some( file( "src/main/resources/application.icns" ))

appbundle.javaOptions ++= Seq( "-ea", "-Xmx2048m" )

appbundle.target <<= baseDirectory


