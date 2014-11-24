package sbtdocker

import org.scalatest.{FunSuite, Matchers}
import sbt.file
import sbtdocker.Instructions._

class DockerfileLikeSuite extends FunSuite with Matchers {
  val allInstructions = Seq(
    From("image"),
    Maintainer("marcus"),
    Run("echo", "docker"),
    Run.shell("echo", "docker"),
    Cmd("cmd", "arg"),
    Cmd.shell("cmd", "arg"),
    Expose(80, 8080),
    Env("key", "value"),
    Add("a", "b"),
    Copy("a", "b"),
    EntryPoint("entrypoint", "arg"),
    EntryPoint.shell("entrypoint", "arg"),
    Volume("mountpoint"),
    User("marcus"),
    WorkDir("path"),
    OnBuild(Run("echo", "123"))
  )

  test("Instructions string is in sequence and matches instructions") {
    val dockerfile = immutable.Dockerfile(allInstructions)

    dockerfile.mkString shouldEqual
      """FROM image
        |MAINTAINER marcus
        |RUN ["echo", "docker"]
        |RUN echo docker
        |CMD ["cmd", "arg"]
        |CMD cmd arg
        |EXPOSE 80 8080
        |ENV key value
        |ADD a b
        |COPY a b
        |ENTRYPOINT ["entrypoint", "arg"]
        |ENTRYPOINT entrypoint arg
        |VOLUME mountpoint
        |USER marcus
        |WORKDIR path
        |ONBUILD RUN ["echo", "123"]""".stripMargin
  }

  test("addInstruction changes the Dockerfile by adding a instruction to the end") {
    val predefined = immutable.Dockerfile(allInstructions)

    val withAddInstruction =
      allInstructions.foldLeft(immutable.Dockerfile.empty) {
        case (dockerfile, instruction) => dockerfile.addInstruction(instruction)
      }

    withAddInstruction shouldEqual predefined
  }

  test("Instruction methods adds a instruction to the dockerfile") {
    val predefined = immutable.Dockerfile(allInstructions)

    val withMethods = immutable.Dockerfile.empty
      .from("image")
      .maintainer("marcus")
      .run("echo", "docker")
      .runShell("echo", "docker")
      .cmd("cmd", "arg")
      .cmdShell("cmd", "arg")
      .expose(80, 8080)
      .env("key", "value")
      .addRaw("a", "b")
      .copyRaw("a", "b")
      .entryPoint("entrypoint", "arg")
      .entryPointShell("entrypoint", "arg")
      .volume("mountpoint")
      .user("marcus")
      .workDir("path")
      .onBuild(Run("echo", "123"))

    withMethods shouldEqual predefined
  }

  test("Run, Cmd and EntryPoint instructions should handle arguments with whitespace") {
    val dockerfile = immutable.Dockerfile.empty
      .run("echo", "arg \"with\t\nspaces")
      .runShell("echo", "arg \"with\t\nspaces")
      .cmd("echo", "arg \"with\t\nspaces")
      .cmdShell("echo", "arg \"with\t\nspaces")
      .entryPoint("echo", "arg \"with\t\nspaces")
      .entryPointShell("echo", "arg \"with\t\nspaces")

    dockerfile.mkString shouldEqual
      """RUN ["echo", "arg \"with\t\nspaces"]
        |RUN echo "arg \"with\t\nspaces"
        |CMD ["echo", "arg \"with\t\nspaces"]
        |CMD echo "arg \"with\t\nspaces"
        |ENTRYPOINT ["echo", "arg \"with\t\nspaces"]
        |ENTRYPOINT echo "arg \"with\t\nspaces"""".stripMargin
  }

  test("Add and copy a file to /") {
    val sourceFile = file("/tmp/abc/xyz/")
    val dockerfile = immutable.Dockerfile.empty
      .add(sourceFile, "/")
      .copy(sourceFile, "/")

    dockerfile.instructions should contain theSameElementsInOrderAs Seq(
      Add("/xyz", "/"),
      Copy("/xyz", "/"))
    dockerfile.stagedFiles should contain theSameElementsInOrderAs Seq(
      StageFile(sourceFile, file("/xyz")),
      StageFile(sourceFile, file("/xyz")))
  }

  test("Add and copy a file to a specified destination") {
    val sourceFile = file("/tmp/xyz")
    val d = immutable.Dockerfile.empty
      .add(sourceFile, "abc")
      .copy(sourceFile, "xyz")

    d.instructions should contain theSameElementsInOrderAs Seq(
      Add("abc", "abc"),
      Copy("xyz", "xyz"))
    d.stagedFiles should contain theSameElementsInOrderAs Seq(
      StageFile(sourceFile, file("abc")),
      StageFile(sourceFile, file("xyz")))
  }

  test("Adding a single source file to multiple paths") {
    val sourceFile = file("/a/b/c/d")
    val dockerfile = immutable.Dockerfile.empty
      .add(sourceFile, "/x/y")
      .add(sourceFile, "/z")
      .add(sourceFile, "/z")
      .copy(sourceFile, "/x/y")
      .copy(sourceFile, "/z")
      .copy(sourceFile, "/z")

    dockerfile.instructions should contain theSameElementsInOrderAs Seq(
      Add("/x/y", "/x/y"),
      Add("/z", "/z"),
      Add("/z", "/z"),
      Copy("/x/y", "/x/y"),
      Copy("/z", "/z"),
      Copy("/z", "/z"))
    dockerfile.stagedFiles should contain theSameElementsInOrderAs Seq(
      StageFile(sourceFile, file("/x/y")),
      StageFile(sourceFile, file("/z")),
      StageFile(sourceFile, file("/z")),
      StageFile(sourceFile, file("/x/y")),
      StageFile(sourceFile, file("/z")),
      StageFile(sourceFile, file("/z")))
  }

  test("OnBuild instruction should correctly generate instruction string") {
    val onBuild = OnBuild(Run("echo", "123"))

    onBuild.toString shouldEqual """ONBUILD RUN ["echo", "123"]"""
  }

  test("Run, EntryPoint and Cmd should support shell format") {
    Run.shell("a", "b", "\"c\"").toString shouldEqual """RUN a b \"c\""""
    EntryPoint.shell("a", "b", "\"c\"").toString shouldEqual """ENTRYPOINT a b \"c\""""
    Cmd.shell("a", "b", "\"c\"").toString shouldEqual """CMD a b \"c\""""
  }

  test("Run, EntryPoint and Cmd should support exec format") {
    Run("a", "b", "\"c\"").toString shouldEqual """RUN ["a", "b", "\"c\""]"""
    EntryPoint("a", "b", "\"c\"").toString shouldEqual """ENTRYPOINT ["a", "b", "\"c\""]"""
    Cmd("a", "b", "\"c\"").toString shouldEqual """CMD ["a", "b", "\"c\""]"""
  }
}
