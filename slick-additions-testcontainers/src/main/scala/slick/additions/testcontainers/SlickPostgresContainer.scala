package slick.additions.testcontainers

import scala.concurrent.Future

import slick.ControlsConfig
import slick.future.Database
import slick.jdbc.{DatabaseConfig, JdbcDatabaseConfig, JdbcProfile, PostgresProfile}

import com.typesafe.config.ConfigFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName


/** A PostgreSQL [[https://testcontainers.com/ TestContainers]] container with convenience methods to connect to it from
  * Slick.
  *
  * @param imageName
  *   the `DockerImageName`. Defaults to `postgres,` but it can another compatible image too. See
  *   [[https://java.testcontainers.org/modules/databases/postgres/]] for details.
  */
//noinspection ScalaUnusedSymbol,ScalaWeakerAccess,StructuralWrap
class SlickPostgresContainer(imageName: DockerImageName = DockerImageName.parse(PostgreSQLContainer.IMAGE))
    extends PostgreSQLContainer[SlickPostgresContainer](imageName) {

  /** The port that can be used to connect to the database from the host
    *
    * @see
    *   [[getHost]]
    */
  def port = getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)

  /** Returns a Slick `DatabaseConfig` that connects to the database. Pass it to `Database.open` or `Database.use` of
    * whichever Slick facade you use (`slick.future.Database`, `slick.cats.Database`, `slick.zio.Database`).
    *
    * @param profile
    *   the Slick profile, defaults to `PostgresProfile`. Pass your own profile to get a config typed for it.
    * @param databaseName
    *   Optionally specify a database name. Otherwise [[getDatabaseName]] will be used.
    * @param maxConnections
    *   The maximum number of concurrent connections (default 20)
    * @param queueSize
    *   The maximum number of actions waiting for a connection slot before being rejected. Defaults to 1000.
    *
    * @see
    *   [[ControlsConfig]]
    */
  def slickDatabaseConfig[P <: JdbcProfile](
    profile: P = PostgresProfile,
    databaseName: String = getDatabaseName,
    maxConnections: Int = 20,
    queueSize: Int = 1000
  ): JdbcDatabaseConfig[P] =
    DatabaseConfig
      .forURL(
        profile,
        url = s"jdbc:postgresql://$getHost:$port/$databaseName",
        user = getUsername,
        password = getPassword,
        driver = "org.postgresql.Driver"
      )
      .withControls(ControlsConfig(maxConnections = maxConnections, queueSize = queueSize))

  /** Opens a `Future`-based Slick Database object that can connect to the database and run Slick actions. The caller is
    * responsible for calling `close()` on it.
    *
    * @param databaseName
    *   Optionally specify a database name. Otherwise [[getDatabaseName]] will be used.
    * @param maxConnections
    *   The maximum number of concurrent connections (default 20)
    * @param queueSize
    *   The maximum number of actions waiting for a connection slot before being rejected. Defaults to 1000.
    *
    * @see
    *   [[slickDatabaseConfig]]
    */
  def slickDatabase(
    databaseName: String = getDatabaseName,
    maxConnections: Int = 20,
    queueSize: Int = 1000
  ): Future[Database] =
    Database.open(slickDatabaseConfig(PostgresProfile, databaseName, maxConnections, queueSize))

  /** Returns a Typesafe Config object that describes how to connect to the database. It can be passed to
    * `DatabaseConfig.forConfig` to get a `DatabaseConfig`.
    *
    * @param databaseName
    *   optionally specify a database name other than the TestContainers default
    *
    * @example
    *   {{{
    *     val container = new SlickPostgresContainer
    *     container.start()
    *
    *     import slick.future.Database
    *     import slick.jdbc.{DatabaseConfig, PostgresProfile}
    *     val database = Database.open(DatabaseConfig.forProfileConfig(PostgresProfile, "", container.slickConfig))
    *   }}}
    */
  def slickConfig(databaseName: String = getDatabaseName) =
    ConfigFactory
      .parseString(
        // language=hocon
        s"""dataSourceClass = "org.postgresql.ds.PGSimpleDataSource"
           |properties {
           |  serverName = $getHost
           |  portNumber = $getFirstMappedPort
           |  databaseName = $getDatabaseName
           |  user = $getUsername
           |  password = $getPassword
           |}
           |profile = "slick.jdbc.PostgresProfile$$"
           |
           |maxConnections = 10
           |""".stripMargin
      )
}
