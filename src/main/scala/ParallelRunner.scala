import cats.effect.{ExitCode, FiberIO, IO, IOApp}
import cats.effect.std.Dispatcher
import cats.implicits.toTraverseOps

import java.lang.management.ManagementFactory
import java.util.UUID

trait ParallelRunner extends IOApp {

  def doSomethingBlocking(dispatcher: Dispatcher[IO]): IO[Unit] =
    for {
      _ <- IO.delay {  dispatcher.unsafeRunSync(IO(UUID.randomUUID())) }
    } yield ()

  def runProcesses(cycles: Int, d: Dispatcher[IO]): IO[Unit] =
    for {
      // each fiber repeats action
      _ <- (1 to cycles).toList.traverse(_ => doSomethingBlocking(d))
    } yield ()

  def runAll(processes: Int, cycles: Int, d: Dispatcher[IO]): IO[List[FiberIO[Unit]]] =
    for {
      // 100 parallel fibers
      fibers     <- (1 to processes).toList.traverse(id => runProcesses(cycles, d).start)
    } yield fibers

  def countThreads: String = {
    val threadMXBean = ManagementFactory.getThreadMXBean
    (for (threadId <- threadMXBean.getAllThreadIds) yield
      threadId -> threadMXBean.getThreadInfo(threadId).getThreadState.name)
      .toMap
      .groupBy(_._2)         // Group by values
      .view.mapValues(_.size)     // Count occurrences for each value
      .map { case (k,v) => s"$k: $v"}.mkString(",")
  }

  override def run(args: List[String]): IO[ExitCode] = Dispatcher.parallel[IO].use( d =>
    for {
      now <- IO(System.currentTimeMillis)
      processes = args(0).toInt
      cycles = args(1).toInt
      fibers     <- runAll(processes, cycles, d)
      _ <- fibers.traverse(_.joinWithNever)
      elapsed <- IO(System.currentTimeMillis - now)
      _ <- IO(println(s"$processes processes, $cycles cycles: elapsed $elapsed ms, threads $countThreads"))
    } yield ExitCode.Success
  )
}
