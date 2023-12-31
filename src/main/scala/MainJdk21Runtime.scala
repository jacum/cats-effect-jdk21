import cats.effect.unsafe.{IORuntime, IORuntimeBuilder}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object MainJdk21Runtime extends ParallelRunner {
  override protected def runtime: IORuntime = {
    val compute          = Executors.newVirtualThreadPerTaskExecutor()
    val executionContext = ExecutionContext.fromExecutor(compute);
    IORuntimeBuilder
      .apply()
      .setCompute(executionContext, () => compute.shutdown())
      .build()
  }
}