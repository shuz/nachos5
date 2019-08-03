package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
	wordSet = false;
	lock = new Lock();
	speaker = new Condition2(lock);
	listener = new Condition2(lock);
	synchronizer = new Condition2(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
	lock.acquire();
	while (wordSet)
	    speaker.sleep();
	this.word = word;
	wordSet = true;
	listener.wake();	// wake a listener waiting for message
	synchronizer.sleep();	// wait until listener came
	lock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */
    public int listen() {
	lock.acquire();
	while (!wordSet)
	    listener.sleep();
	int word = this.word;
	wordSet = false;
	speaker.wake();		// wake a speaker waiting to speak
	synchronizer.wake();	// wake a finished speaker
	lock.release();
    	return word;
    }
    
    private static class Speaker implements Runnable {
	public Speaker(Communicator c, int word) {
	    this.c = c;
	    this.word = word;
	}
	
	public void run() {
	    System.out.println("*** thread " + KThread.currentThread() + " speaking " + word);
	    c.speak(word);
	    System.out.println("*** thread " + KThread.currentThread() + " spoken " + word);
	}
	
	private Communicator c;
	private int word;
    }
    
    private static class Listener implements Runnable {
	public Listener(Communicator c) {
	    this.c = c;
	}
	
	public void run() {
	    System.out.println("*** thread " + KThread.currentThread() + " listening");
	    int word = c.listen();
	    System.out.println("*** thread " + KThread.currentThread() + " heard " + word);
	}
	
	private Communicator c;
    }
    
    public static void selfTest() {
	System.out.println("Communicator.selfTest()");
	
	Communicator c = new Communicator();
	{
	    System.out.println("*** 1. speakers come first");
            KThread speaker1 = new KThread(new Speaker(c, 100)).setName("speaker 1");
            KThread speaker2 = new KThread(new Speaker(c, 200)).setName("speaker 2");
            KThread speaker3 = new KThread(new Speaker(c, 300)).setName("speaker 3");
            speaker1.fork(); speaker2.fork(); speaker3.fork();
            for (int i = 0; i < 10; ++i)
                KThread.currentThread().yield();
            KThread listener1 = new KThread(new Listener(c)).setName("listener 1");
            KThread listener2 = new KThread(new Listener(c)).setName("listener 2");
            KThread listener3 = new KThread(new Listener(c)).setName("listener 3");
            listener1.fork(); listener2.fork(); listener3.fork();
            
            speaker1.join(); speaker2.join(); speaker3.join();
            listener1.join(); listener2.join(); listener3.join();
            System.out.println();
	}
	
	{
	    System.out.println("*** 2. listeners come first");
            KThread listener1 = new KThread(new Listener(c)).setName("listener 1");
            KThread listener2 = new KThread(new Listener(c)).setName("listener 2");
            KThread listener3 = new KThread(new Listener(c)).setName("listener 3");
            listener1.fork(); listener2.fork(); listener3.fork();
	    for (int i = 0; i < 10; ++i)
                KThread.currentThread().yield();
            KThread speaker1 = new KThread(new Speaker(c, 100)).setName("speaker 1");
            KThread speaker2 = new KThread(new Speaker(c, 200)).setName("speaker 2");
            KThread speaker3 = new KThread(new Speaker(c, 300)).setName("speaker 3");
            speaker1.fork(); speaker2.fork(); speaker3.fork();

            speaker1.join(); speaker2.join(); speaker3.join();
            listener1.join(); listener2.join(); listener3.join();
            System.out.println();
	}
	
	{
	    System.out.println("*** 3. mixed situation");
	    KThread speaker1 = new KThread(new Speaker(c, 100)).setName("speaker 1");
            KThread speaker2 = new KThread(new Speaker(c, 200)).setName("speaker 2");
            KThread speaker3 = new KThread(new Speaker(c, 300)).setName("speaker 3");
            KThread listener1 = new KThread(new Listener(c)).setName("listener 1");
            KThread listener2 = new KThread(new Listener(c)).setName("listener 2");
            KThread listener3 = new KThread(new Listener(c)).setName("listener 3");
            
            // fork all the threads atomically
            boolean intStatus = Machine.interrupt().disable();
            speaker1.fork();  speaker2.fork();
            listener1.fork(); speaker3.fork();
            listener2.fork(); listener3.fork();
            Machine.interrupt().setStatus(intStatus);
            
            speaker1.join(); speaker2.join(); speaker3.join();
            listener1.join(); listener2.join(); listener3.join();
            System.out.println();
	}
    }
    
    private boolean wordSet;
    private int word;
    private Lock lock;
    private Condition2 speaker, listener, synchronizer;
}
