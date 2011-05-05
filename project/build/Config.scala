import sbt._

class ConfigProject(info: ProjectInfo) extends DefaultProject(info) {

  val junit = "junit" % "junit" % "4.8.2" % "test"
  val spec = "org.scala-tools.testing" % "specs_2.8.1" % "1.6.7" % "test"
  val apacheHttp = "org.apache.httpcomponents" % "httpclient" % "4.0-beta2" % "compile"
  val snakeyaml = "org.yaml" % "snakeyaml" % "1.8"
}
