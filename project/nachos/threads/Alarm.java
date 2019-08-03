package nachos.threads;

import nachos.machine.*;

import java.util.TreeMap;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	waitingThreads = new TreeMap();
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
	boolean intStatus = Machine.interrupt().disable();
	
	while (!waitingThreads.isEmpty()) {
	    Long time = (Long)waitingThreads.firstKey();
	    if (time.longValue() <= Machine.timer().getTime()) {
		LinkedList list = (LinkedList)waitingThreads.get(time);
		Iterator itor = list.iterator();
		while (itor.hasNext()) {
		    KThread thread = (KThread)itor.next();
		    thread.ready();
		}
		waitingThreads.remove(time);
	    } else {
		break;
	    }
	}
	
	Machine.interrupt().setStatus(intStatus);
	
	KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	boolean intStatus = Machine.interrupt().disable();

	Long wakeTime = new Long(Machine.timer().getTime() + x);
	LinkedList list = (LinkedList)waitingThreads.get(wakeTime);
	if (list == null) {
	    list = new LinkedList();
	    waitingThreads.put(wakeTime, list);
	}
	list.addLast(KThread.currentThread());
	
	KThread.sleep();
	
	Machine.interrupt().setStatus(intStatus);
    }
    
    
    private static class PingTest implements Runnable {
	public PingTest(int loops, long time) {
	    this.loops = loops;
	    this.time = time;
	}
	
	public void run() {
    	    for (int i = 0; i < loops; ++i) {
    		System.out.println("*** thread " + KThread.currentThread() 
    			+ " set alarm at " + Machine.timer().getTime());
    		ThreadedKernel.alarm.waitUntil(time);
    		System.out.println("*** thread " + KThread.currentThread()
    			+ " alarmed at " + Machine.timer().getTime());
    	    }
	}
	
	private int loops;
	private long time;
    }
    
    /**
     * Tests whether this module is working.
     */
    public static void selfTest() {
	System.out.println("Alarm.selfTest()");
	
	KThread ping = new KThread(new PingTest(5,  6000)).setName("ping");
	KThread pong = new KThread(new PingTest(10, 2000)).setName("pong");

	ping.fork();
	pong.fork();

	ping.join();
	pong.join();

	KThread ping2 = new KThread(new PingTest(5,  100)).setName("ping hf");
	KThread pong2 = new KThread(new PingTest(10, 200)).setName("pong hf");
	
	ping2.fork();
	pong2.fork();
	
	ping2.join();
	pong2.join();
	
	System.out.println();
    }
    
    private TreeMap waitingThreads;
}
