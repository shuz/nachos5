package nachos.threads;

import java.util.LinkedList;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
	this.waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assert(conditionLock.isHeldByCurrentThread());
	
	boolean intStatus = Machine.interrupt().disable();
	
	conditionLock.release();
	waitQueue.waitForAccess(KThread.currentThread());
	KThread.currentThread().sleep();
	conditionLock.acquire();
	
	Machine.interrupt().setStatus(intStatus);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	Lib.assert(conditionLock.isHeldByCurrentThread());
	
	boolean intStatus = Machine.interrupt().disable();
	
	KThread thread = waitQueue.nextThread();
	if (thread != null) thread.ready();
	
	Machine.interrupt().setStatus(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	Lib.assert(conditionLock.isHeldByCurrentThread());
	
	boolean intStatus = Machine.interrupt().disable();
	
	KThread thread = null;
	while ((thread = waitQueue.nextThread()) != null)
	    thread.ready();
	
	Machine.interrupt().setStatus(intStatus);
    }
    
    private static class PingTest implements Runnable {
	PingTest(Lock lock, Condition2 condition, int loops) {
	    this.lock = lock;
	    this.condition = condition;
	    this.loops = loops;
	}
	
	public void run() {
    	    lock.acquire();
    	    for (int i = 0; i < loops; ++i) {
                System.out.println("*** thread " + KThread.currentThread() 
            	    + " sleep (" + i + ")" );
                condition.sleep();
                System.out.println("*** thread " + KThread.currentThread() 
            	    + " waked (" + i + ")" );
                for (int j = 0; j < 10; ++j)
            	KThread.yield();
            }
            System.out.println("*** thread " + KThread.currentThread()
            	+ " finished");
            lock.release();
	}
	
	private Lock lock;
	private Condition2 condition;
	private int loops;
    }
    
    /**
     * Tests whether this module is working.
     */
    public static void selfTest() {
	System.out.println("Condition2.selfTest()");
	
	Lock lock = new Lock();
	lock.acquire();
	
	Condition2 condition = new Condition2(lock);
	
	KThread ping = new KThread(new PingTest(lock, condition, 5)).setName("ping");
	KThread pong = new KThread(new PingTest(lock, condition, 3)).setName("pong");
	
	boolean intStatus = Machine.interrupt().disable();
	ThreadedKernel.scheduler.setPriority(ping, 2);
	ThreadedKernel.scheduler.setPriority(pong, 3);
	ping.fork();
	pong.fork();
	
	Machine.interrupt().restore(intStatus);
	
	for (int i = 0; i < 2; ++i) {	// wake both threads 4 times
	    condition.wakeAll();	// involked before sleep() at least once
	    System.out.println("*** wake all involked");
	    lock.release();
	    
	    for (int j = 0; j < 10; ++j)
		KThread.yield();	// wait for ping pong threads to run
	    lock.acquire();
	}
	
	while (!ping.finished() || !pong.finished()) {
	    condition.wake();
	    System.out.println("*** wake involked");
	    lock.release();

	    for (int j = 0; j < 10; ++j)
		KThread.yield();	// wait for ping pong threads to run
	    lock.acquire();
	}
	lock.release();
	
	Lib.assert(ping.finished());
	Lib.assert(pong.finished());
	
	System.out.println();
    }
    
    private Lock conditionLock;
    private ThreadQueue waitQueue;
}
