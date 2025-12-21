package org.ergoplatform.ergoscript.project

import io.circe._
import io.circe.parser._
import io.circe.generic.semiauto._
import java.nio.file.{Files, Path, Paths}
import scala.util.{Try, Success, Failure}
import com.typesafe.scalalogging.LazyLogging

/** Parser for ErgoScript project configuration files (ergo.json).
  */
object ProjectConfigParser extends LazyLogging {

  // Circe decoders for project config structures
  implicit val constantDefinitionDecoder: Decoder[ConstantDefinition] =
    deriveDecoder[ConstantDefinition]
  implicit val contractCompileTargetDecoder: Decoder[ContractCompileTarget] =
    deriveDecoder[ContractCompileTarget]
  implicit val compileConfigDecoder: Decoder[CompileConfig] =
    deriveDecoder[CompileConfig]
  implicit val testConfigDecoder: Decoder[TestConfig] =
    deriveDecoder[TestConfig]
  implicit val ergoScriptConfigDecoder: Decoder[ErgoScriptConfig] =
    deriveDecoder[ErgoScriptConfig]
  implicit val directoryConfigDecoder: Decoder[DirectoryConfig] =
    deriveDecoder[DirectoryConfig]

  // Custom decoder for ProjectConfig to handle the nested constants map
  implicit val projectConfigDecoder: Decoder[ProjectConfig] =
    new Decoder[ProjectConfig] {
      final def apply(c: HCursor): Decoder.Result[ProjectConfig] = {
        for {
          name <- c.downField("name").as[String]
          version <- c.downField("version").as[String]
          description <- c.downField("description").as[Option[String]]
          ergoscript <- c
            .downField("ergoscript")
            .as[Option[ErgoScriptConfig]]
            .map(_.getOrElse(ErgoScriptConfig()))
          directories <- c
            .downField("directories")
            .as[Option[DirectoryConfig]]
            .map(_.getOrElse(DirectoryConfig()))
          constants <- c
            .downField("constants")
            .as[Option[Map[String, ConstantDefinition]]]
            .map(_.getOrElse(Map.empty))
          compile <- c.downField("compile").as[Option[CompileConfig]]
          test <- c.downField("test").as[Option[TestConfig]]
        } yield ProjectConfig(
          name,
          version,
          description,
          ergoscript,
          directories,
          constants,
          compile,
          test
        )
      }
    }

  // Encoders for serialization
  implicit val constantDefinitionEncoder: Encoder[ConstantDefinition] =
    deriveEncoder[ConstantDefinition]
  implicit val contractCompileTargetEncoder: Encoder[ContractCompileTarget] =
    deriveEncoder[ContractCompileTarget]
  implicit val compileConfigEncoder: Encoder[CompileConfig] =
    deriveEncoder[CompileConfig]
  implicit val testConfigEncoder: Encoder[TestConfig] =
    deriveEncoder[TestConfig]
  implicit val ergoScriptConfigEncoder: Encoder[ErgoScriptConfig] =
    deriveEncoder[ErgoScriptConfig]
  implicit val directoryConfigEncoder: Encoder[DirectoryConfig] =
    deriveEncoder[DirectoryConfig]
  implicit val projectConfigEncoder: Encoder[ProjectConfig] =
    deriveEncoder[ProjectConfig]

  /** Parse a project configuration file.
    *
    * @param path
    *   Path to the ergo.json file
    * @return
    *   Parsed project configuration or error
    */
  def parseFile(path: Path): Either[String, ProjectConfig] = {
    Try {
      val content = Files.readString(path)
      parse(content).flatMap(_.as[ProjectConfig])
    } match {
      case Success(Right(config)) =>
        logger.info(s"Successfully parsed project config from $path")
        Right(config)
      case Success(Left(error)) =>
        logger.error(s"Failed to parse project config: $error")
        Left(s"JSON parsing error: ${error.getMessage}")
      case Failure(ex) =>
        logger.error(s"Failed to read project config file: $ex")
        Left(s"Failed to read file: ${ex.getMessage}")
    }
  }

  /** Parse a project configuration from JSON string.
    *
    * @param json
    *   JSON string
    * @return
    *   Parsed project configuration or error
    */
  def parseJson(json: String): Either[String, ProjectConfig] = {
    parse(json).flatMap(_.as[ProjectConfig]) match {
      case Right(config) => Right(config)
      case Left(error)   => Left(s"JSON parsing error: ${error.getMessage}")
    }
  }

  /** Find and parse a project configuration file in the given directory.
    * Searches for ergo.json or ergoproject.json.
    *
    * @param directory
    *   Directory to search in
    * @return
    *   Parsed project configuration or None if not found
    */
  def findAndParse(directory: Path): Option[ProjectConfig] = {
    val candidates = List("ergo.json", "ergoproject.json")

    candidates
      .map(name => directory.resolve(name))
      .find(path => Files.exists(path) && Files.isRegularFile(path))
      .flatMap { path =>
        parseFile(path) match {
          case Right(config) => Some(config)
          case Left(error) =>
            logger.warn(s"Found project file but failed to parse: $error")
            None
        }
      }
  }

  /** Serialize a project configuration to JSON string.
    *
    * @param config
    *   Project configuration
    * @param pretty
    *   Whether to pretty-print the JSON
    * @return
    *   JSON string
    */
  def toJson(config: ProjectConfig, pretty: Boolean = true): String = {
    import io.circe.syntax._
    val json = config.asJson
    if (pretty) {
      json.spaces2
    } else {
      json.noSpaces
    }
  }

  /** Create a default project configuration.
    *
    * @param name
    *   Project name
    * @param description
    *   Project description
    * @return
    *   Default project configuration
    */
  def defaultConfig(
      name: String = "my-ergo-project",
      description: String = "An ErgoScript project"
  ): ProjectConfig = {
    ProjectConfig(
      name = name,
      version = "1.0.0",
      description = Some(description),
      ergoscript = ErgoScriptConfig(
        version = "6.0",
        network = "mainnet"
      ),
      directories = DirectoryConfig(
        source = "src",
        lib = "lib",
        output = "build",
        tests = "tests"
      ),
      constants = Map(
        "minBoxValue" -> ConstantDefinition(
          constantType = "Long",
          value = "1000000",
          description = Some("Minimum box value in nanoErgs")
        )
      ),
      compile = Some(
        CompileConfig(
          contracts = List(
            ContractCompileTarget(
              name = "MainContract",
              source = "src/main.es",
              output = "main.json"
            )
          )
        )
      ),
      test = Some(
        TestConfig(
          enabled = true,
          parallel = false,
          timeout = 30000L
        )
      )
    )
  }
}
