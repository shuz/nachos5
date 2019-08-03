package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assert(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assert(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assert(Machine.interrupt().disabled());
		       
	Lib.assert(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();

	boolean increased = false;
	
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority < priorityMaximum) {
	    setPriority(thread, priority+1);
	    increased = true;
	}

	Machine.interrupt().restore(intStatus);
	return increased;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();

	boolean decreased = false;
	
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority > priorityMinimum) {
	    setPriority(thread, priority-1);
	    decreased = true;
	}

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return decreased;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;
    
    /**
     * The number of priority levels.
     */
    public static final int priorityLevels = priorityMaximum - priorityMinimum + 1;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
	PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}

	public void waitForAccess(KThread thread) {
	    Lib.assert(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assert(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assert(Machine.interrupt().disabled());

	    if (getGroup(maxGroup) == null) {
		if (holder != null) {
		    holder.holdingQueues.remove(this);
		    holder.resetEffectivePriority();
		    holder = null;
		}
		return null;
	    }
	    
	    KThread thread = getGroup(maxGroup).thread;
	    getGroup(maxGroup).unlink();
	    acquire(thread);
	    return thread;
	}
	
	protected void searchMaxGroup() {
	    for (maxGroup = priorityMaximum; maxGroup > priorityMinimum; --maxGroup) {
		if (getGroup(maxGroup) != null) break;
	    }
	}
	
	/**
	 * denote the priority to holder
	 */
	protected void donatePriority() {
	    Lib.assert(transferPriority);
            if (holder.effectivePriority < maxGroup) {
                holder.effectivePriority = maxGroup;
                holder.updateQueue();
            }
	}
	
	public void print() {
	    Lib.assert(Machine.interrupt().disabled());
	    for (int i = maxGroup; i <= priorityMaximum; ++i) {
		if (getGroup(i) == null) continue;
		
		System.out.print("priority " + i + ": ");
		ThreadState state = getGroup(i);
		ThreadState end = state;
		do {
		    System.out.print("\t" + state.thread.toString());
		    state = state.next;
		} while (state != end);
		System.out.println();
	    }
	}
	
	protected ThreadState getGroup(int priority) {
	    return groups[priority - priorityMinimum];
	}
	
	protected void setGroup(int priority, ThreadState state) {
	    groups[priority - priorityMinimum] = state;
	}
	
	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
	
	protected ThreadState [] groups = new ThreadState[priorityLevels];
	protected int maxGroup = priorityMinimum;
	
	protected ThreadState holder;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
	    effectivePriority = priorityDefault;
	    priority = priorityDefault;
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    return effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    	    
	    if (effectivePriority < priority) {
		effectivePriority = priority;
		updateQueue();
	    }
	}
	
	/**
	 * Reset the effective priority of the associated thread to 
	 * the maximum of <tt>priority</tt> and <tt>maxGroup</tt>s in 
	 * <ttholdingQueues</tt>.
	 */
	protected void resetEffectivePriority() {
	    int oldep = effectivePriority;
	    effectivePriority = priority;
	    
	    Iterator itor = holdingQueues.iterator();
	    while (itor.hasNext()) {
		PriorityQueue queue = (PriorityQueue)itor.next();
		if (queue.maxGroup > effectivePriority)
		    effectivePriority = queue.maxGroup;
	    }
	    
	    if (oldep != effectivePriority) updateQueue();
	}
	
	/**
	 * Update the position of this thread in <tt>queue</tt>
	 * according to the new <tt>effectivePriority</tt>.
	 */
	protected void updateQueue() {
            if (queue != null) {
                PriorityQueue q = queue;
                unlink();
                link(q);
            }	    
	}
	
	/**
	 * Hook method to adjust the queue when this thread is linked into it
	 * @param q the queue into which this thread is linked
	 */
	protected void adjustQueueOnLink(PriorityQueue q) {
	    if (q.maxGroup < effectivePriority) {
		q.maxGroup = effectivePriority;
        	if (q.transferPriority)
        	    q.donatePriority();
	    }
	}

	/**
	 * Hook method to adjust the queue when this thread is unlinked from it
	 * @param q the queue from which this thread is unlinked
	 */
	protected void adjustQueueOnUnlink(PriorityQueue q) {
	    if (q.getGroup(q.maxGroup) == null) {
		q.searchMaxGroup();
		if (q.transferPriority)
		    q.donatePriority();
	    }
	}
	
	/**
	 * Link the state to the back of the priority queue,
	 * and update <tt>q.maxGroup</tt> if necessary
	 * 
	 * @param q	the queue to insert back
	 */
	protected void link(PriorityQueue q) {
	    Lib.assert(queue == null && next == null && prev == null);
	    if (q.getGroup(effectivePriority) == null) {
		q.setGroup(effectivePriority, this);
		next = prev = this;
	    } else {
		next = q.getGroup(effectivePriority);
		prev = next.prev;
		next.prev = prev.next = this;
	    }
	    
	    adjustQueueOnLink(q);
	    queue = q;
	}
	
	/**
	 * Unlink the state from the current priority queue,
	 * and update <tt>queue.maxGroup</tt> if necessary
	 * set <tt>next</tt>, <tt>prev</tt> and <tt>queue</tt> to <tt>null</tt>.
	 * If this is the heading element,
	 * modify <tt>queue.groups[effectivePriority]</tt> as well
	 */
	protected void unlink() {
	    prev.next = next;
	    next.prev = prev;
	    
	    if (this == queue.getGroup(effectivePriority)) {
		queue.setGroup(effectivePriority, next);
		if (next == this) {
		    queue.setGroup(effectivePriority, null);
		}
	    }

	    adjustQueueOnUnlink(queue);
	    
	    prev = next = null;
	    queue = null;
	}
	
	/**
	 * Hook method, called when this thread no longer holds the resource
	 * managed by waitQueue and the queue denotes priority
	 * @param waitQueue the queue whose resource is taken back from this thread 
	 */
	protected void releaseResource(PriorityQueue waitQueue) {
	    Lib.assert(waitQueue.transferPriority);
	    holdingQueues.remove(waitQueue);
	    resetEffectivePriority();
	}

	/**
	 * Hook method, called when this thread becomes the holder of 
	 * the resource managed by waitQueue and the queue denotes priority
	 * @param waitQueue the queue whose resource is given to this thread 
	 */
	protected void takeResource(PriorityQueue waitQueue) {
	    Lib.assert(waitQueue.transferPriority);
	    waitQueue.holder = this;
	    holdingQueues.addLast(waitQueue);
	    resetEffectivePriority();
	}
	
	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    Lib.assert(waitQueue.transferPriority ? 
		    waitQueue.holder != null : waitQueue.holder == null);
	    Lib.assert(waitQueue.holder != this); 
	    
	    link(waitQueue);
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    if (waitQueue.transferPriority) {
		if (waitQueue.holder != null) 
		    waitQueue.holder.releaseResource(waitQueue);
		
		if (waitQueue.transferPriority)
		    takeResource(waitQueue);
	    }
	}
	
	public String toString() {
	    return ("p: " + priority + " ep: " + effectivePriority);
	}
	
	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	/** The effective priority of the associated thread. */
	protected int effectivePriority;
	
	/** Links to next and previous ThreadState in the same priority group. */
	protected ThreadState next;
	protected ThreadState prev;
	
	/** The priority queue holding this thread state. */
	protected PriorityQueue queue;
	
	/** The priority queues whose <tt>holder</tt> == <tt>this</tt>. */
	protected LinkedList holdingQueues = new LinkedList();
    }

    private static class PingTest implements Runnable {
	PingTest(int n) {
	    this.n = n;
	}
	
	private int n;
	
	public void run() {
	    for (int i=0; i<n; i++) {
		System.out.println("*** thread " + KThread.currentThread() + " looped "
				   + i + " times");
		KThread.currentThread().yield();
		
		if (i == n/2) ThreadedKernel.scheduler.increasePriority();
	    }
	    System.out.println("*** thread " + KThread.currentThread() + " finished");
	}
    }
    
    public static void selfTest() {
	// note: priority donating test is combined with multiple-join test
	//       and is performed in KThread.selfTest()
	
	System.out.println("PriorityScheduler.selfTest()");
	
        KThread ping = new KThread(new PingTest(10)).setName("ping thread");
        KThread ping2 = new KThread(new PingTest(10)).setName("ping2 thread");
        KThread pong = new KThread(new PingTest(10)).setName("pong thread");
        
        boolean intStatus = Machine.interrupt().disable();
        
        ThreadedKernel.scheduler.setPriority(ping, 2);
        ThreadedKernel.scheduler.setPriority(ping2, 2);
        
        ping.fork();
        ping2.fork();
        pong.fork();
        
        Machine.interrupt().restore(intStatus);
        	
        ping.join();
        pong.join();
        
        Lib.assert(ping.finished());
        Lib.assert(pong.finished());
        
        System.out.println();
    }
}
