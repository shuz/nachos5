package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.LinkedList;
import java.util.Random;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }
    
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new LotteryQueue(transferPriority);
    }
    
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new LotteryThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }
    
    /*
     * here we reuse PriorityQueue.groups by using groups[0] only
     */
    protected class LotteryQueue extends PriorityQueue {
	LotteryQueue(boolean transferPriority) {
	    super(transferPriority);
	}

	public KThread nextThread() {
	    Lib.assert(Machine.interrupt().disabled());

	    if (groups[0] == null) return null;
	    
	    int winner = Lib.random(getTotalTickets());
	    
    	    LotteryThreadState candidate = (LotteryThreadState)groups[0];
    	    do {
		winner -= candidate.totalTickets;
		if (winner < 0) {
		    candidate.unlink();
		    acquire(candidate.thread);
		    return candidate.thread;
		}
		candidate = (LotteryThreadState)candidate.next;
    	    } while (candidate != groups[0]);

	    Lib.assertNotReached();
	    return null;
	}
	
	protected void adjustTickets(int num) {
	    if (num == 0) return;
	    LotteryQueue queue = this;
	    do {
		queue.maxGroup += num;
		if (queue.holder != null) {
		    ((LotteryThreadState)queue.holder).totalTickets += num;
		    queue = (LotteryQueue)queue.holder.queue;
		} else {
		    queue = null;
		}
	    } while (queue != null);
	}
	
	protected int getTotalTickets() {
    	    return maxGroup;
	}
	
	public void print() {
	    if (groups[0] == null) return;
            ThreadState state = groups[0];
            ThreadState end = state;
            do {
                System.out.print("\t" + state.thread.toString());
                state = state.next;
            } while (state != end);
            System.out.println();
	}
    }
    
    /*
     * note: here we reuse priority+1 as ticket holding by the thread, 
     *       effectivePriority is set constantly to 0,
     *       and new variable totalTickets as the total ticket holding.        
     *       the 1 more ticket is intended to allow threads 
     *       whose priority == 0 have the chance to run.
     */
    protected class LotteryThreadState extends ThreadState {
	
	public LotteryThreadState(KThread thread) {
	    super(thread);
	    Lib.assert(priorityMinimum >= 0);
	    effectivePriority = priorityMinimum;
	    priority = priorityDefault;
	    totalTickets = priority + 1;
	}

	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    int delta = priority - this.priority;
	    this.priority = priority;
	    totalTickets += delta;
	    if (queue != null) ((LotteryQueue)queue).adjustTickets(delta);
	}
	
	protected void adjustQueueOnLink(PriorityQueue q) {
	    ((LotteryQueue)q).adjustTickets(totalTickets);
	}
	
	protected void adjustQueueOnUnlink(PriorityQueue q) {
	    ((LotteryQueue)q).adjustTickets(-totalTickets);
	}
	
	protected void releaseResource(PriorityQueue waitQueue) {
	    Lib.assert(waitQueue.transferPriority);
	    holdingQueues.remove(waitQueue);
	    int delta = -((LotteryQueue)waitQueue).getTotalTickets();
	    totalTickets += delta;
	    if (queue != null) ((LotteryQueue)queue).adjustTickets(delta);
	}
	
	protected void takeResource(PriorityQueue waitQueue) {
	    Lib.assert(waitQueue.transferPriority);
	    waitQueue.holder = this;
	    holdingQueues.addLast(waitQueue);
	    int delta = ((LotteryQueue)waitQueue).getTotalTickets();
	    totalTickets += delta;
	    if (queue != null) ((LotteryQueue)queue).adjustTickets(delta);
	}
	
	public String toString() {
	    return ("t: " + (int)(priority+1) + " tt: " + totalTickets);
	}
	
	protected int totalTickets;
    }
}
