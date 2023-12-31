# JDK 21 Virtual threads runtime vs. Cats Effect WorkStealingPool microbenchmark

A small demo to show how JDK 21 Virtual threads are better than Cats Effect native WorkStealingPool in 
handling workloads occasionally entering blocking contexts.

Cats Effect is an amazing piece of high-end machinery enabling clear separation of effects and logics for
complex concurrent asynchronous flows implemented in Scala. Its default executor, `WorkStealingPool` is derived from 
[Tokio Rust](https://tokio.rs/) library and features a pool of just few (as many as CPU cores allocated) worker threads, 
processing _non-blocking runnable thunks_ with very high efficiency. 

While Cats Effect provides second pool for _explicit blocking_ operations, defined by `IO.blocking` sections,
blocking contexts can occur in many places, especially when code written for Cats Effect needs to interact with
legacy asynchronous implementation, based on Scala `Future` or Akka actors. 

Often, a forced conversion of an IO effect into a value needs to happen _synchronously_ - this is done 
by summoning `Dispatcher` and using its `unsafeRunSync()` method. Internally, this forces a creation of `Future` and the current thread waits on a lock, awaiting result of
`Future` execution. 

Fortunately, there is a smart built-in mechanism to deal with blocking contexts:

- When some `io-compute-NNN` thread hits a blocking context, it re-labels itself into `io-compute-blocker-NNN` and
bravely remains dealing with the blocking runnable thunk. 

- To avoid depletion of `io-compute` threads, a clone of current thread is made and put back into 
the main `compute` pool. After blocking part is done, the current `io-compute-blocker-NNN` thread keeps lingering
for another minute, waiting for other blocking thunks to come along.

This all works rather smoothly when blocking contexts occur at _at reasonably continuous rate_.
But when the blocking runnables start coming in bursts, there are no available `io-compute-blocker-NNN` threads
available, and more and more threads get created, hardly competing for the scarce CPU cores, causing runaway
positive feedback reaction.

There is no effective remedy against it - even if inside `unsafeRunSync()` nothing really blocking happens, 
it is the overhead of uncapped creating the threads and Future/awaits that could bring otherwise healthy process to a nearly halt.

Fortunately, new JDK 21 (latest LTS after JDK 17) has just arrived, featuring [Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).

It was quite a simple exercise to replace default Cats Effect `runtime` with JDK 21-based one.

There were no substantial difference measured on non-blocking effects, but for the `unsafeRunSync()`, 
JDK 21 beats default executor with flying colors.

Below is some code to 

Default runner:
- [MainDefaultCatsWorkStealingPool.scala](src%2Fmain%2Fscala%2FMainDefaultCatsWorkStealingPool.scala)

JDK 21 Runner:
- [MainJdk21Runtime.scala](src%2Fmain%2Fscala%2FMainJdk21Runtime.scala)

Uses simple drop-in replacement runtime:
```scala
  override protected def runtime: IORuntime = {
    val compute          = Executors.newVirtualThreadPerTaskExecutor()
    val executionContext = ExecutionContext.fromExecutor(compute);
    IORuntimeBuilder
      .apply()
      .setCompute(executionContext, () => compute.shutdown())
      .build()
  }
```

The test code can be seen in [ParallelRunner.scala](src%2Fmain%2Fscala%2FParallelRunner.scala)

Final results:

| Parallel processes         | WorkStealingPool time, ms | JDK 21 Virtual threads time,ms | %% better |
|----------------------------|--------------------------|--------------------------|----|
|10|969|870|11,38%|
|20|1387|1131|22,63%|
|50|2436|1573|54,86%|
|100|4129|2422|70,48%|
|200|7918|3987|98,60%|
|500|39538|9235|328,13%|

The more effects happen in parallel, the more substantial the overhead of creating extra threads becomes.

The number of `io-compute-blocker-NNN` threads in each test becomes ~number of parallel processes, 
e.g. for test with 200 processes.

> This approach has recently been tested in production workloads as well. The only caveat is that by default Cats Effect
tracing attaches something to each thread, and with JDK 21 virtual thread, it causes a memory leak.
With `-Dcats.effect.tracing.mode=NONE`, fiber tracing is disabled and there is no leak anymore.
