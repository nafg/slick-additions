package slick.additions.testcontainers

import slick.jdbc.JdbcBackend
import slick.util.AsyncExecutor

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

  /** Returns a Slick Database object that can connect to the database and run Slick actions.
    *
    * @param backend
    *   a [[JdbcBackend]]. Optional, but it may be necessary to pass e.g., `MyProfile.backend` to infer the right
    *   path-dependent type.
    * @param databaseName
    *   Optionally specify a database name. Otherwise [[getDatabaseName]] will be used.
    * @param numThreads
    *   The size of Slick's thread pool (default 20)
    * @param queueSize
    *   The job queue size, 0 for direct hand-off or -1 for unlimited size. Defaults to 1000.
    *
    * @see
    *   [[AsyncExecutor]]
    */
  def slickDatabase(
    backend: JdbcBackend = JdbcBackend,
    databaseName: String = getDatabaseName,
    numThreads: Int = 20,
    queueSize: Int = 1000
  ) =
    backend.Database.forURL(
      url = s"jdbc:postgresql://$getHost:$port/$databaseName",
      user = getUsername,
      password = getPassword,
      driver = "org.postgresql.Driver",
      executor = AsyncExecutor(s"slick-postgres-testcontainers-$getContainerId", numThreads, queueSize)
    )

  /** Returns a Typesafe Config object that describes how to connect to the database. It can be passed to
    * `Database.forConfig` to get a Database object.
    *
    * @param databaseName
    *   optionally specify a database name other than the TestContainers default
    *
    * @example
    *   {{{
    *     val container = new SlickPostgresContainer
    *     container.start()
    *
    *     import slick.jdbc.PostgresProfile.api.*
    *     val database = Database.forConfig("", container.slickConfig)
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
           |numThreads = 10
           |maxConnections = 10
           |""".stripMargin
      )
}
