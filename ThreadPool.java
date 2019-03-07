package il.co.ilrd.threadpool;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import il.co.ilrd.waitablequeue.WaitableQueueCV;

public class ThreadPool implements Executor {
/*--  Static fields ThreadPool---------------*/
	public enum Priority {

		LOW(1), MEDIUM(2), HIGH(3);

		private Priority(int pValue) {
			this.pValue = pValue;
		}

		private int pValue;
	}
	
/*-- Inner Classes ThreadPool --------------------*/
	private class WorkerThread extends Thread {
		private boolean shouldRun = true;
		private Task<?> tsk;
		
		@Override
		public void run() {
			while (shouldRun) {
				try {
					tsk = waitableQueue.dequeue();
				} catch (InterruptedException e) {
					throw new AssertionError("Problem in the implementation of Dequeue!", e);
				}
				assert (tsk.state.get() == Task.TaskState.NEW 
						|| tsk.state.get() == Task.TaskState.CANCELLED);

				if (tsk.state.compareAndSet(Task.TaskState.NEW, Task.TaskState.RUNNING)) {

					tsk.doTask(); 	
					}}
			
			if(isShutDown) {
				barrier.countDown();	
			}}					
		  
		 private void removeThread() { 
			  shouldRun = false; 
			  }}

	private static class Task<T> implements Comparable<ThreadPool.Task<?>> {
/*-- Fields Task---------------------------------------*/
		private final int priority;
		private final TaskFuture future;

		private Callable<T> job;
		private Exception exception;
		private T returnValue;
		private AtomicReference<TaskState> state = new AtomicReference<>(TaskState.NEW);
		
/*-- Methods Task-----------------------------------------*/
		private enum TaskState {
			NEW, RUNNING, CANCELLED, EXCEPTION, DONE;
		}
		
		Task(Callable<T> job, int priority) {
			this.job = job;
			this.priority = priority;
			future = new TaskFuture();
		}

		void doTask() {
			future.lock.lock();
			try {
				returnValue = job.call();
				state.set(TaskState.DONE);
			} 
			catch (Exception e) {
				exception = e;
				state.set(TaskState.EXCEPTION);
			}
			finally {
				future.finished.signalAll();
				future.lock.unlock();
				job = null;
			}}


		@Override
		public int compareTo(Task<?> o) {
			
			return o.priority - this.priority;
		}
/*-- Inner Class in Task----------------------------------------------*/
		class TaskFuture implements Future<T> {
/*-- Fields TaskFuture-----------------------------------------------*/
			private final Lock lock = new ReentrantLock();
			private final Condition finished = lock.newCondition();
/*-- Methods TaskFuture--------------------------------------------------*/
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return (state.compareAndSet(TaskState.NEW, TaskState.CANCELLED));
					
				}

			@Override
			public boolean isCancelled() {

				return (state.get() == TaskState.CANCELLED);
			}

			@Override
			public boolean isDone() {

				return (state.get() != Task.TaskState.NEW 
						&& state.get() != Task.TaskState.RUNNING);
			}

			@Override
			public T get() throws InterruptedException, ExecutionException {

				lock.lock();
				try {
					while(!isDone()) {
						finished.await();
					}	
				}
				finally {
					lock.unlock();
				}
				
				switch (state.get()) {
					case CANCELLED: throw new CancellationException();
					case EXCEPTION: throw new ExecutionException(exception);
					case DONE: return returnValue;
					default: throw new AssertionError();
					}}

			@Override
			public T get(long timeout, TimeUnit unit)
					throws InterruptedException, ExecutionException, TimeoutException {
				long nanosTimeout = unit.toNanos(timeout);
				
				lock.lock();
				try {
					while(!isDone()) {
						finished.awaitNanos(nanosTimeout);
					}	
				}
				finally {
					lock.unlock();
				}
				switch (state.get()) {
				case CANCELLED: throw new CancellationException();
				case EXCEPTION: throw new ExecutionException(exception);
				case DONE: return returnValue;
				default:  throw new AssertionError();
				}}}}
			
/*-- fields -ThreadPool--------------------------*/

	private final static WaitableQueueCV<Task<?>> waitableQueue = new WaitableQueueCV<>();
	private final Semaphore pauseSem = new Semaphore(0);
	private volatile boolean isShutDown = false;
	private CountDownLatch barrier = null;
	private int nThreads;
	private boolean isPaused = false;

/*-- Methods -ThreadPool-------------------------*/

	public ThreadPool(int nThreads) {
		this.nThreads = nThreads;
		addThreads(nThreads);
	}

	@Override
	public void execute(Runnable command) {

		submit(command, Priority.MEDIUM);
	}

	public <T> Future<T> submit(Callable<T> job) {

		return submit(job, Priority.MEDIUM);
	}

	public Future<?> submit(Runnable job, Priority p) {
		Objects.requireNonNull(job, "job can't be null");
		return submit(job, p, null);
	}

	public <T> Future<T> submit(Runnable job, Priority p, T result) {
		
		return (submit(() -> {job.run(); return result;}, p));
	}

	public <T> Future<T> submit(Callable<T> job, Priority p) {
		Task<T> tsk = new Task<>(Objects.requireNonNull(job, "job can't be null"), p.pValue);
		
		if (isShutDown) {
			
			throw new RejectedExecutionException("can't add tasks while shutting down");
		}
		waitableQueue.enqueue(tsk);
		
		return tsk.future;	
	}
	
	public void setNumOfThreads(int nThreads) {
//		if(isPaused) {
//			return;
//		}
		
		int threadDiffrence = nThreads - this.nThreads;
		if (threadDiffrence < 0) {
			for(int i = 0; i < (threadDiffrence * -1); ++i) {
				Callable<?> setCallable = getRemoveTaskCallable();
				Task<?> setTask = new Task<>(setCallable, Priority.HIGH.pValue + 1);
				waitableQueue.enqueue(setTask);
			}
		}
		else {
			addThreads(threadDiffrence);
		}}

	public void pause() {
		isPaused = true;
		
		for(int i = 0; i < nThreads; ++i) {
			Task<?> pauseTask = new Task<>(()-> {pauseSem.acquire(); return null;}, Priority.HIGH.pValue + 2);
			waitableQueue.enqueue(pauseTask);
		}}

	public void resume() {
			pauseSem.release(nThreads);
	}

	public void shutdown() {
		isShutDown = true;
		barrier = new CountDownLatch(nThreads);
		
		if(isPaused == true) {
			resume();
		}
		
		for(int i = nThreads; i > 0; --i) {
			Task<?> shoutDownTask = new Task<>(getRemoveTaskCallable(), 0);
			waitableQueue.enqueue(shoutDownTask);
		}}

	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		
		if(isShutDown && barrier.await(timeout, unit)) {
			
			return true;
		}
		return false;
	}
/*-- Private Methods ThreadPool ------------------------*/
	
	private void addThreads(int threadsToAdd) {
		
		for(int i = threadsToAdd; i > 0; --i) {
			new WorkerThread().start();			
		}}
	
	private Callable<?> getRemoveTaskCallable() {
		
		return (() -> {((WorkerThread)Thread.currentThread()).removeThread(); return null;});
	}}
